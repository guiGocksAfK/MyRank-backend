package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.AiInsight;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.insight.InsightChatMessageDTO;
import br.com.myrank.dto.insight.InsightGenerateRequestDTO;
import br.com.myrank.dto.insight.InsightPayloadDTO;
import br.com.myrank.dto.insight.InsightResponseDTO;
import br.com.myrank.repository.AiInsightRepository;
import br.com.myrank.repository.WorkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InsightService {

    /** Teto de obras mandadas pro modelo — segura o custo de token e o tempo de resposta. */
    private static final int MAX_WORKS = 100;
    /** Gerações novas por usuário por dia (cache hit não conta). */
    private static final int DAILY_LIMIT = 5;
    /** Perguntas de follow-up permitidas por análise. */
    private static final int CHAT_LIMIT = 3;
    /** Tamanho máximo de uma pergunta do chat. */
    private static final int CHAT_MAX_CHARS = 500;

    private final WorkRepository workRepository;
    private final AiInsightRepository insightRepository;
    private final GeminiClient gemini;
    private final ObjectMapper objectMapper;

    public InsightService(WorkRepository workRepository,
                          AiInsightRepository insightRepository,
                          GeminiClient gemini,
                          ObjectMapper objectMapper) {
        this.workRepository = workRepository;
        this.insightRepository = insightRepository;
        this.gemini = gemini;
        this.objectMapper = objectMapper;
    }

    /** Última análise gerada pelo usuário, ou vazio se ele nunca gerou. */
    @Transactional(readOnly = true)
    public Optional<InsightResponseDTO> getLatest(Long userId) {
        return insightRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(entity -> InsightResponseDTO.of(entity, deserialize(entity), true,
                        deserializeChat(entity), CHAT_LIMIT));
    }

    /**
     * Responde uma pergunta de follow-up sobre a análise {@code insightId}.
     * Máx. {@link #CHAT_LIMIT} perguntas por análise; o par pergunta/resposta
     * fica salvo em {@code chat_log}.
     */
    @Transactional
    public InsightResponseDTO chat(Long userId, Long insightId, String rawQuestion) {
        AiInsight insight = insightRepository.findById(insightId)
                .filter(i -> i.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Análise não encontrada."));

        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("Escreva uma pergunta.");
        }
        if (question.length() > CHAT_MAX_CHARS) {
            question = question.substring(0, CHAT_MAX_CHARS);
        }

        List<InsightChatMessageDTO> thread = deserializeChat(insight);
        long asked = thread.stream().filter(InsightChatMessageDTO::isUser).count();
        if (asked >= CHAT_LIMIT) {
            throw new IllegalArgumentException(
                    "Você já usou as " + CHAT_LIMIT + " perguntas desta análise. Gere uma nova análise para conversar de novo.");
        }

        InsightPayloadDTO analysis = deserialize(insight);
        String answer = gemini.chat(
                InsightPromptBuilder.CHAT_SYSTEM,
                InsightPromptBuilder.chatContext(analysis),
                thread,
                question);

        thread.add(new InsightChatMessageDTO(InsightChatMessageDTO.USER, question, LocalDateTime.now()));
        thread.add(new InsightChatMessageDTO(InsightChatMessageDTO.AI, answer, LocalDateTime.now()));
        insight.setChatLog(serializeChat(thread));
        insight = insightRepository.save(insight);

        return InsightResponseDTO.of(insight, analysis, true, thread, CHAT_LIMIT);
    }

    @Transactional
    public InsightResponseDTO generate(Long userId, InsightGenerateRequestDTO req) {
        List<Work> works = selectWorks(userId, req.workIds());
        if (works.isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos uma obra para analisar.");
        }

        String hash = selectionHash(works);

        if (!req.refresh()) {
            Optional<AiInsight> cached = insightRepository.findByUserIdAndSelectionHash(userId, hash);
            if (cached.isPresent()) {
                return InsightResponseDTO.of(cached.get(), deserialize(cached.get()), true,
                        deserializeChat(cached.get()), CHAT_LIMIT);
            }
        }

        LocalDate today = LocalDate.now();
        long usedToday = insightRepository.countByUserIdAndCreatedAtAfter(userId, today.atStartOfDay());
        if (usedToday >= DAILY_LIMIT) {
            throw new IllegalArgumentException(
                    "Você atingiu o limite de " + DAILY_LIMIT + " análises por dia. Tente de novo amanhã.");
        }

        InsightPayloadDTO payload = gemini.analyze(
                InsightPromptBuilder.SYSTEM,
                InsightPromptBuilder.user(works));

        AiInsight entity = insightRepository.findByUserIdAndSelectionHash(userId, hash)
                .orElseGet(() -> new AiInsight(userId, hash, gemini.model(), works.size(), null));
        entity.setModel(gemini.model());
        entity.setWorkCount(works.size());
        entity.setPayload(serialize(payload));
        entity.setChatLog("[]"); // análise nova → chat de follow-up recomeça do zero
        entity = insightRepository.save(entity);

        return InsightResponseDTO.of(entity, payload, false, List.of(), CHAT_LIMIT);
    }

    /** Obras do usuário; se vierem ids, filtra por eles. Limita a MAX_WORKS (melhores por nota final). */
    private List<Work> selectWorks(Long userId, List<Long> workIds) {
        List<Work> all = workRepository.findByUserIdOrderByFinalScoreDesc(userId);
        if (workIds != null && !workIds.isEmpty()) {
            Set<Long> wanted = Set.copyOf(workIds);
            all = all.stream().filter(w -> wanted.contains(w.getId())).collect(Collectors.toList());
        }
        if (all.size() > MAX_WORKS) {
            all = all.subList(0, MAX_WORKS);
        }
        return all;
    }

    /** SHA-256 de (modelo + obras ordenadas por id + notas). Mesma seleção → mesmo hash → cache. */
    private String selectionHash(List<Work> works) {
        String basis = works.stream()
                .sorted(Comparator.comparing(Work::getId))
                .map(w -> w.getId() + ":" + w.getScore() + ":" + w.getFinalScore())
                .collect(Collectors.joining("|", gemini.model() + "#", ""));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(basis.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular o hash da seleção.", e);
        }
    }

    private String serialize(InsightPayloadDTO payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar a análise.", e);
        }
    }

    private InsightPayloadDTO deserialize(AiInsight entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), InsightPayloadDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a análise salva.", e);
        }
    }

    private String serializeChat(List<InsightChatMessageDTO> thread) {
        try {
            return objectMapper.writeValueAsString(thread);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar o chat da análise.", e);
        }
    }

    /** Chat salvo em {@code chat_log}; tolera nulo/vazio devolvendo lista mutável vazia. */
    private List<InsightChatMessageDTO> deserializeChat(AiInsight entity) {
        String raw = entity.getChatLog();
        if (raw == null || raw.isBlank() || raw.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            List<InsightChatMessageDTO> parsed = objectMapper.readValue(
                    raw, new TypeReference<List<InsightChatMessageDTO>>() {});
            return new ArrayList<>(parsed);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
