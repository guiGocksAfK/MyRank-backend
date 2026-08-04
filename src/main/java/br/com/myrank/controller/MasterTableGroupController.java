package br.com.myrank.controller;

import br.com.myrank.domain.entity.MasterTableGroup;
import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.MasterTableGroupCreateDTO;
import br.com.myrank.dto.MasterTableGroupResponseDTO;
import br.com.myrank.dto.MasterTableGroupUpdateDTO;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.MasterTableGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/master-table-groups")
public class MasterTableGroupController {

    private final MasterTableGroupService groupService;
    private final AuthUtils authUtils;

    public MasterTableGroupController(MasterTableGroupService groupService, AuthUtils authUtils) {
        this.groupService = groupService;
        this.authUtils = authUtils;
    }

    @PostMapping
    public ResponseEntity<MasterTableGroupResponseDTO> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody MasterTableGroupCreateDTO dto) {
        User user = authUtils.getUser(userDetails);
        MasterTableGroup group = groupService.createGroup(user, dto);
        return ResponseEntity.ok(MasterTableGroupResponseDTO.fromEntity(group));
    }

    @GetMapping
    public ResponseEntity<List<MasterTableGroupResponseDTO>> getMyGroups(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authUtils.getUser(userDetails);
        List<MasterTableGroupResponseDTO> groups = groupService.getGroupsByUser(user.getId())
                .stream().map(MasterTableGroupResponseDTO::fromEntity).toList();
        return ResponseEntity.ok(groups);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MasterTableGroupResponseDTO> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody MasterTableGroupUpdateDTO dto) {
        User user = authUtils.getUser(userDetails);
        MasterTableGroup group = groupService.updateGroup(id, user.getId(), dto);
        return ResponseEntity.ok(MasterTableGroupResponseDTO.fromEntity(group));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User user = authUtils.getUser(userDetails);
        groupService.deleteGroup(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}