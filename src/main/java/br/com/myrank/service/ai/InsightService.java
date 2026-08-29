package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.AiInsight;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.insight.InsightGenerateRequestDTO;
import br.com.myrank.dto.insight.InsightPayloadDTO;
import br.com.myrank.dto.insight.InsightResponseDTO;
import br.com.myrank.repository.AiInsightRepository;
import br.com.myrank.repository.WorkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
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
                .map(entity -> InsightResponseDTO.of(entity, deserialize(entity), true));
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
                return InsightResponseDTO.of(cached.get(), deserialize(cached.get()), true);
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
        entity = insightRepository.save(entity);

        return InsightResponseDTO.of(entity, payload, false);
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
}
