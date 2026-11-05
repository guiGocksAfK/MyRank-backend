package br.com.myrank.controller;

import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.creator.CreatorProfileDTO;
import br.com.myrank.dto.creator.CreatorProfilesRequestDTO;
import br.com.myrank.dto.creator.CreatorUpdateDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.CreatorProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Foto e tipo dos criadores (tela de Criadores). O ranking em si é montado no front. */
@RestController
@RequestMapping("/api/creators")
public class CreatorController {

    private final CreatorProfileService creatorProfileService;
    private final AuthUtils authUtils;

    public CreatorController(CreatorProfileService creatorProfileService, AuthUtils authUtils) {
        this.creatorProfileService = creatorProfileService;
        this.authUtils = authUtils;
    }

    /** Fotos (automática ou a do usuário) e tipo escolhido dos criadores pedidos. */
    @PostMapping("/profiles")
    public ResponseEntity<List<CreatorProfileDTO>> profiles(@AuthenticationPrincipal UserDetails userDetails,
                                                            @Valid @RequestBody CreatorProfilesRequestDTO request) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(creatorProfileService.profiles(user.getId(), request));
    }

    /** Edita a foto e/ou o tipo de um criador, só pro usuário logado. */
    @PutMapping("/profile")
    public ResponseEntity<CreatorProfileDTO> update(@AuthenticationPrincipal UserDetails userDetails,
                                                    @Valid @RequestBody CreatorUpdateDTO dto) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(creatorProfileService.update(user.getId(), dto));
    }
}
