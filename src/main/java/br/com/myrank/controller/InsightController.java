package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.insight.InsightChatRequestDTO;
import br.com.myrank.dto.insight.InsightGenerateRequestDTO;
import br.com.myrank.dto.insight.InsightResponseDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.ai.InsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final InsightService insightService;
    private final AuthUtils authUtils;

    public InsightController(InsightService insightService, AuthUtils authUtils) {
        this.insightService = insightService;
        this.authUtils = authUtils;
    }

    /** Gera (ou reaproveita do cache) a análise de IA para as obras selecionadas. */
    @PostMapping("/generate")
    public ResponseEntity<InsightResponseDTO> generate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody InsightGenerateRequestDTO body) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(insightService.generate(user.getId(), body));
    }

    /** Última análise que o usuário já gerou (para abrir a página direto). */
    @GetMapping("/latest")
    public ResponseEntity<InsightResponseDTO> latest(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        return insightService.getLatest(user.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Pergunta de follow-up sobre a análise (máx. 3 por análise). */
    @PostMapping("/{id}/chat")
    public ResponseEntity<InsightResponseDTO> chat(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody InsightChatRequestDTO body) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(insightService.chat(user.getId(), id, body.question()));
    }
}
