package br.com.myrank.service;

import br.com.myrank.domain.entity.Category;
import br.com.myrank.domain.entity.MasterTableGroup;
import br.com.myrank.domain.entity.UserCreator;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.CreatorRankingDTO;
import br.com.myrank.dto.WorkMiniDTO;
import br.com.myrank.repository.CategoryRepository;
import br.com.myrank.repository.MasterTableGroupRepository;
import br.com.myrank.repository.UserCreatorRepository;
import br.com.myrank.repository.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Os dois rankings que o site montava só no front: o geral (várias tabelas juntas) e o
 * de criadores. Aqui eles ficam com a mesma regra para quem pedir — o bot de Discord
 * usa estas rotas, e o front pode passar a usar também. As regras estão em
 * docs/ranking.md.
 */
@Service
public class RankingService {

    /** Nome do agrupamento que guarda a seleção e a ordem manual do ranking geral. */
    public static final String UNIFIED_GROUP = "Unificado";

    static final double VOLUME_BONUS_CAP = 1.0;
    static final double MAX_CREATOR_SCORE = 12.0;
    static final int CREATOR_WORKS_SHOWN = 4;
    static final List<String> CREATOR_TYPES = List.of("Diretor", "Escritor", "Studio", "Criador");

    public enum UnifiedSort { SCORE, WEIGHTED, TIME }

    public enum CreatorOrder { AVG, WEIGHTED, WORKS }

    private final WorkRepository workRepository;
    private final CategoryRepository categoryRepository;
    private final MasterTableGroupRepository groupRepository;
    private final UserCreatorRepository userCreatorRepository;

    public RankingService(WorkRepository workRepository, CategoryRepository categoryRepository,
                          MasterTableGroupRepository groupRepository,
                          UserCreatorRepository userCreatorRepository) {
        this.workRepository = workRepository;
        this.categoryRepository = categoryRepository;
        this.groupRepository = groupRepository;
        this.userCreatorRepository = userCreatorRepository;
    }

    // ── Ranking geral ─────────────────────────────────────────────────────

    /**
     * Obras das tabelas escolhidas no agrupamento "Unificado" (todas, se ele não existir
     * ou estiver vazio), ordenadas por nota pura, ponderada ou tempo. A ordem manual só
     * desempata: a ordenação é estável sobre a lista que já vem na ordem manual.
     *
     * @param tableId só esta tabela (a ordem manual dela são as posições), ou null
     */
    @Transactional(readOnly = true)
    public List<Work> unified(Long userId, UnifiedSort sort, Long tableId) {
        List<Category> categories = categoryRepository.findByUserId(userId);
        Map<Long, Integer> tableIndex = new HashMap<>();
        for (int i = 0; i < categories.size(); i++) tableIndex.put(categories.get(i).getId(), i);

        Set<Long> selected;
        List<String> manualOrder;
        if (tableId != null) {
            if (!tableIndex.containsKey(tableId)) {
                throw new IllegalArgumentException("Tabela não encontrada.");
            }
            selected = Set.of(tableId);
            manualOrder = List.of();
        } else {
            MasterTableGroup group = groupRepository.findByUserId(userId).stream()
                    .filter(g -> UNIFIED_GROUP.equals(g.getName()))
                    .findFirst().orElse(null);
            selected = group == null ? Set.of()
                    : group.getCategories().stream().map(Category::getId).collect(Collectors.toSet());
            manualOrder = group == null || group.getManualOrder() == null
                    ? List.of() : group.getManualOrder();
        }

        // Base, como o front monta: tabela por tabela, cada uma na ordem das posições.
        List<Work> base = workRepository.findByUserId(userId).stream()
                .filter(w -> selected.isEmpty() || selected.contains(w.getCategory().getId()))
                .sorted(Comparator.comparing((Work w) -> tableIndex.getOrDefault(w.getCategory().getId(),
                                Integer.MAX_VALUE))
                        .thenComparing(Work::getPosition, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Work::getId))
                .toList();

        Map<String, Work> byKey = new LinkedHashMap<>();
        base.forEach(w -> byKey.put(unifiedKey(w), w));
        List<Work> ordered = new ArrayList<>();
        for (String key : manualOrder) {
            Work work = byKey.remove(key);
            if (work != null) ordered.add(work);
        }
        ordered.addAll(byKey.values());

        Comparator<Work> comparator = switch (sort) {
            case SCORE -> Comparator.comparing(Work::getScore, Comparator.nullsLast(Comparator.reverseOrder()));
            case WEIGHTED -> Comparator.comparing(Work::getFinalScore, Comparator.nullsLast(Comparator.reverseOrder()));
            case TIME -> Comparator.comparingInt(Work::getTimeMinutes).reversed();
        };
        ordered.sort(comparator); // List.sort é estável: a ordem manual decide os empates
        return ordered;
    }

    /** Chave da ordem manual: "tabelaId:obraId". */
    static String unifiedKey(Work work) {
        return work.getCategory().getId() + ":" + work.getId();
    }

    // ── Ranking de criadores ──────────────────────────────────────────────

    /**
     * Cada pessoa do campo criador conta à parte; quem tem exatamente as mesmas obras
     * vira uma linha só; quem só tem obras que já estão todas com alguém de mais obras
     * não ganha linha. Assim cada obra aparece uma vez, a não ser quando duas carreiras
     * próprias se cruzam.
     *
     * @param type filtro por tipo (Diretor, Escritor, Studio, Criador) ou null
     */
    @Transactional(readOnly = true)
    public List<CreatorRankingDTO> creators(Long userId, CreatorOrder order, String type) {
        if (type != null && !CREATOR_TYPES.contains(type)) {
            throw new IllegalArgumentException("Tipo de criador inválido.");
        }
        List<Entry> entries = groupCreators(workRepository.findByUserIdOrderByFinalScoreDesc(userId));

        Set<String> keys = new HashSet<>();
        entries.forEach(e -> e.members().forEach(m -> keys.add(m.key())));
        Map<String, String> chosenTypes = keys.isEmpty() ? Map.of()
                : userCreatorRepository.findByUserIdAndCreatorKeyIn(userId, keys).stream()
                .filter(uc -> uc.getCreatorType() != null)
                .collect(Collectors.toMap(UserCreator::getCreatorKey, UserCreator::getCreatorType, (a, b) -> a));

        boolean weighted = order == CreatorOrder.WEIGHTED;
        List<Ranked> ranked = new ArrayList<>();
        for (Entry entry : entries) {
            String entryType = typeOf(entry, chosenTypes);
            if (type != null && !type.equals(entryType)) continue;
            ranked.add(new Ranked(entry, entryType, score(entry.works(), weighted)));
        }

        Comparator<Ranked> byScore = Comparator.comparingDouble((Ranked r) -> r.score().value()).reversed();
        Comparator<Ranked> byCount = Comparator.comparingInt((Ranked r) -> r.entry().works().size()).reversed();
        ranked.sort(order == CreatorOrder.WORKS ? byCount.thenComparing(byScore) : byScore.thenComparing(byCount));

        ToDoubleFunction<Work> note = weighted ? w -> num(w.getFinalScore() != null ? w.getFinalScore() : w.getScore())
                : w -> num(w.getScore());
        return ranked.stream().map(r -> new CreatorRankingDTO(
                r.entry().id(),
                r.entry().name(),
                r.type(),
                r.entry().works().size(),
                r.score().raw(),
                r.score().value(),
                r.entry().works().stream()
                        .sorted(Comparator.comparingDouble(note).reversed())
                        .limit(CREATOR_WORKS_SHOWN)
                        .map(RankingService::mini)
                        .toList()
        )).toList();
    }

    record Member(String key, String name, List<Work> works) {}

    record Entry(String id, String name, List<Member> members, List<Work> works) {}

    record Score(double raw, double value) {}

    private record Ranked(Entry entry, String type, Score score) {}

    static List<Entry> groupCreators(List<Work> works) {
        // 1. cada pessoa à parte; grafias iguais (sem acento/maiúscula) se juntam
        Map<String, Map<String, Integer>> spellings = new LinkedHashMap<>();
        Map<String, Map<Long, Work>> worksByPerson = new LinkedHashMap<>();
        for (Work work : works) {
            for (String name : CreatorNames.split(work.getCreator())) {
                String key = CreatorNames.key(name);
                spellings.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(name, 1, Integer::sum);
                worksByPerson.computeIfAbsent(key, k -> new LinkedHashMap<>()).putIfAbsent(work.getId(), work);
            }
        }

        // 2. mesmas obras = mesma entrada
        Map<String, List<Member>> bySignature = new LinkedHashMap<>();
        for (var person : worksByPerson.entrySet()) {
            String name = spellings.get(person.getKey()).entrySet().stream()
                    .max(Map.Entry.comparingByValue()) // empate: a primeira grafia vista
                    .map(Map.Entry::getKey).orElse(person.getKey());
            List<Work> personWorks = List.copyOf(person.getValue().values());
            String signature = personWorks.stream().map(Work::getId).sorted()
                    .map(String::valueOf).collect(Collectors.joining(","));
            bySignature.computeIfAbsent(signature, s -> new ArrayList<>())
                    .add(new Member(person.getKey(), name, personWorks));
        }

        // 3. quem cabe inteiro dentro de alguém maior some
        List<List<Member>> candidates = new ArrayList<>(bySignature.values());
        candidates.sort(Comparator.comparingInt((List<Member> m) -> m.get(0).works().size()).reversed());
        List<Entry> kept = new ArrayList<>();
        for (List<Member> members : candidates) {
            List<Work> entryWorks = members.get(0).works();
            Set<Long> ids = entryWorks.stream().map(Work::getId).collect(Collectors.toSet());
            boolean hosted = kept.stream().anyMatch(other -> other.works().size() > entryWorks.size()
                    && other.works().stream().map(Work::getId).collect(Collectors.toSet()).containsAll(ids));
            if (!hosted) {
                kept.add(new Entry(
                        members.stream().map(Member::key).collect(Collectors.joining("+")),
                        members.stream().map(Member::name).collect(Collectors.joining(" & ")),
                        members, entryWorks));
            }
        }
        return kept;
    }

    /** Média pura das obras com nota; na ponderada, pesa os minutos e soma o bônus de volume. */
    static Score score(List<Work> works, boolean weighted) {
        List<Work> rated = works.stream().filter(w -> num(w.getScore()) > 0).toList();
        if (rated.isEmpty()) return new Score(0, 0);
        double raw = rated.stream().mapToDouble(w -> num(w.getScore())).average().orElse(0);
        if (!weighted) return new Score(raw, raw);
        double totalMinutes = rated.stream().mapToDouble(Work::getTimeMinutes).sum();
        double byTime = totalMinutes > 0
                ? rated.stream().mapToDouble(w -> num(w.getScore()) * w.getTimeMinutes()).sum() / totalMinutes
                : raw;
        double volumeBonus = Math.min(Math.log10(rated.size()), VOLUME_BONUS_CAP);
        return new Score(raw, Math.min(byTime + volumeBonus, MAX_CREATOR_SCORE));
    }

    /** O tipo escolhido pela pessoa, se todos da linha concordam; senão o deduzido das obras. */
    static String typeOf(Entry entry, Map<String, String> chosenTypes) {
        List<String> chosen = entry.members().stream().map(m -> chosenTypes.get(m.key())).toList();
        String first = chosen.get(0);
        if (first != null && chosen.stream().allMatch(first::equals)) return first;
        Map<String, Integer> counts = new LinkedHashMap<>();
        entry.works().forEach(w -> counts.merge(creatorType(w), 1, Integer::sum));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("Criador");
    }

    /** Tipo de criador que cada template sugere (a pessoa pode trocar no site). */
    static String creatorType(Work work) {
        if (work.getTemplate() == null) return "Criador";
        return switch (work.getTemplate().value().toLowerCase(Locale.ROOT)) {
            case "book", "manga" -> "Escritor";
            case "game", "anime" -> "Studio";
            case "movie", "tv" -> "Diretor";
            default -> "Criador";
        };
    }

    private static WorkMiniDTO mini(Work work) {
        String type = work.getTemplate() == null ? null : work.getTemplate().type();
        return new WorkMiniDTO(work.getId(), work.getTitle(), type, work.getImageUrl(), work.getScore());
    }

    private static double num(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }
}
