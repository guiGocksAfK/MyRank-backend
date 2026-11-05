package br.com.myrank.controller;

import jakarta.validation.Valid;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.RefreshDetailsResultDTO;
import br.com.myrank.dto.WorkCreateDTO;
import br.com.myrank.dto.WorkResponseDTO;
import br.com.myrank.dto.WorkUpdateDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.WorkDetailsBackfillService;
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
    private final WorkDetailsBackfillService backfillService;
    private final AuthUtils authUtils;

    public WorkController(WorkService workService, WorkDetailsBackfillService backfillService, AuthUtils authUtils) {
        this.workService = workService;
        this.backfillService = backfillService;
        this.authUtils = authUtils;
    }

    /** "Atualizar dados" em todas as obras da conta de uma vez (gêneros das obras antigas). */
    @PostMapping("/refresh-details")
    public ResponseEntity<RefreshDetailsResultDTO> refreshDetails(@AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        return ResponseEntity.ok(backfillService.refreshAll(user.getId()));
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