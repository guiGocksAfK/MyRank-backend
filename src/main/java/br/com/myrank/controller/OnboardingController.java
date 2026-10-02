package br.com.myrank.controller;

import br.com.myrank.dto.CategoryResponseDTO;
import br.com.myrank.dto.OnboardingTablesDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AuthUtils authUtils;

    public OnboardingController(OnboardingService onboardingService, AuthUtils authUtils) {
        this.onboardingService = onboardingService;
        this.authUtils = authUtils;
    }

    @PostMapping("/tables")
    public ResponseEntity<List<CategoryResponseDTO>> chooseTables(
            @AuthenticationPrincipal UserDetails principal, @RequestBody OnboardingTablesDTO dto) {
        return ResponseEntity.ok(onboardingService.chooseTables(authUtils.getUser(principal).getId(), dto.templates()));
    }

    @PostMapping("/finish")
    public ResponseEntity<Void> finish(@AuthenticationPrincipal UserDetails principal) {
        onboardingService.finish(authUtils.getUser(principal).getId());
        return ResponseEntity.noContent().build();
    }
}
