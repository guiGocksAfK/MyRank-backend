package br.com.myrank.controller;

import jakarta.validation.Valid;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.WorkCreateDTO;
import br.com.myrank.dto.WorkResponseDTO;
import br.com.myrank.dto.WorkUpdateDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.WorkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/works")
public class WorkController {

    private final WorkService workService;
    private final AuthUtils authUtils;

    public WorkController(WorkService workService, AuthUtils authUtils) {
        this.workService = workService;
        this.authUtils = authUtils;
    }

    @PostMapping
    public ResponseEntity<WorkResponseDTO> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WorkCreateDTO dto) {
        User user = authUtils.getUser(userDetails);
        Work work = workService.createWork(user, dto);
        return ResponseEntity.ok(WorkResponseDTO.fromEntity(work));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<WorkResponseDTO>> getByCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long categoryId) {
        User user = authUtils.getUser(userDetails);
        List<WorkResponseDTO> works = workService.getWorksByCategory(categoryId, user.getId())
                .stream().map(WorkResponseDTO::fromEntity).toList();
        return ResponseEntity.ok(works);
    }

    @GetMapping("/unified")
    public ResponseEntity<List<WorkResponseDTO>> getUnified(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        List<WorkResponseDTO> works = workService.getUnifiedWorks(user.getId())
                .stream().map(WorkResponseDTO::fromEntity).toList();
        return ResponseEntity.ok(works);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkResponseDTO> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody WorkUpdateDTO dto) {
        User user = authUtils.getUser(userDetails);
        Work work = workService.updateWork(id, user.getId(), dto);
        return ResponseEntity.ok(WorkResponseDTO.fromEntity(work));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User user = authUtils.getUser(userDetails);
        workService.deleteWork(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}