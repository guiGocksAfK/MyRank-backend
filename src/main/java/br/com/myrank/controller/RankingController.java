package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.CreatorRankingDTO;
import br.com.myrank.dto.WorkResponseDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.RankingService;
import br.com.myrank.service.RankingService.CreatorOrder;
import br.com.myrank.service.RankingService.UnifiedSort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** Ranking geral e ranking de criadores, já ordenados (ver docs/ranking.md). */
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;
    private final AuthUtils authUtils;

    public RankingController(RankingService rankingService, AuthUtils authUtils) {
        this.rankingService = rankingService;
        this.authUtils = authUtils;
    }

    /**
     * {@code sort}: score (nota pura), weighted (ponderada) ou time. Sem ele, vale a
     * configuração "nota ponderada ligada" da conta, como no site. Com {@code table},
     * só aquela tabela (que precisa ser da pessoa).
     */
    @GetMapping("/unified")
    public ResponseEntity<List<WorkResponseDTO>> unified(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long table) {
        User user = authUtils.getUser(userDetails);
        UnifiedSort chosen = sort == null || sort.isBlank()
                ? (user.isWeightedByDefault() ? UnifiedSort.WEIGHTED : UnifiedSort.SCORE)
                : parse(UnifiedSort.class, sort, "Ordenação inválida.");
        return ResponseEntity.ok(rankingService.unified(user.getId(), chosen, table).stream()
                .map(WorkResponseDTO::fromEntity).toList());
    }

    /** {@code order}: avg (média), weighted (ponderada) ou works (mais obras). */
    @GetMapping("/creators")
    public ResponseEntity<List<CreatorRankingDTO>> creators(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "avg") String order,
            @RequestParam(required = false) String type) {
        User user = authUtils.getUser(userDetails);
        CreatorOrder chosen = parse(CreatorOrder.class, order, "Ordenação inválida.");
        String typeFilter = type == null || type.isBlank() ? null : type.trim();
        return ResponseEntity.ok(rankingService.creators(user.getId(), chosen, typeFilter));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String message) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message);
        }
    }
}
