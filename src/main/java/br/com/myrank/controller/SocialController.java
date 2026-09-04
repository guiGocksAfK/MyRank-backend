package br.com.myrank.controller;

import jakarta.validation.Valid;
import br.com.myrank.domain.entity.User;
import br.com.myrank.dto.*;
import br.com.myrank.security.AuthUtils;
import br.com.myrank.service.social.SocialService;
import br.com.myrank.service.social.TakeCommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/social")
public class SocialController {

    private final SocialService socialService;
    private final TakeCommentService takeCommentService;
    private final AuthUtils authUtils;

    public SocialController(SocialService socialService, TakeCommentService takeCommentService,
                            AuthUtils authUtils) {
        this.socialService = socialService;
        this.takeCommentService = takeCommentService;
        this.authUtils = authUtils;
    }

    @GetMapping("/summary")
    public ResponseEntity<SocialSummaryDTO> summary(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(socialService.getSummary(me(ud)));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<FeedItemDTO>> feed(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(socialService.getFeed(me(ud), page, size));
    }

    @GetMapping("/following")
    public ResponseEntity<List<SocialUserDTO>> following(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(socialService.getFollowing(me(ud)));
    }

    @GetMapping("/followers")
    public ResponseEntity<List<SocialUserDTO>> followers(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(socialService.getFollowers(me(ud)));
    }

    @GetMapping("/followers/recent")
    public ResponseEntity<List<SocialUserDTO>> recentFollowers(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(socialService.getRecentFollowers(me(ud), limit));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<SocialUserDTO>> suggestions(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(socialService.getSuggestions(me(ud)));
    }

    @GetMapping("/users")
    public ResponseEntity<List<SocialUserDTO>> search(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(name = "q", defaultValue = "") String query) {
        return ResponseEntity.ok(socialService.searchUsers(me(ud), query));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<SocialProfileDTO> profile(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(socialService.getProfile(me(ud), id));
    }

    @PostMapping("/follow/{id}")
    public ResponseEntity<SocialUserDTO> toggleFollow(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(socialService.toggleFollow(me(ud), id));
    }

    @GetMapping("/follow-requests")
    public ResponseEntity<List<SocialUserDTO>> followRequests(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(socialService.getFollowRequests(me(ud)));
    }

    @PostMapping("/follow-requests/{requesterId}/approve")
    public ResponseEntity<Void> approveFollowRequest(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long requesterId) {
        socialService.approveFollowRequest(me(ud), requesterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/follow-requests/{requesterId}/reject")
    public ResponseEntity<Void> rejectFollowRequest(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long requesterId) {
        socialService.rejectFollowRequest(me(ud), requesterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/feed/{eventId}/react")
    public ResponseEntity<ReactionSummaryDTO> react(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long eventId,
            @Valid @RequestBody ReactRequestDTO body) {
        return ResponseEntity.ok(socialService.react(me(ud), eventId, body.kind()));
    }

    @PostMapping("/takes")
    public ResponseEntity<FeedItemDTO> postTake(
            @AuthenticationPrincipal UserDetails ud, @Valid @RequestBody PostTakeDTO body) {
        return ResponseEntity.ok(socialService.postTake(me(ud), body));
    }

    @PatchMapping("/takes/{takeId}")
    public ResponseEntity<FeedItemDTO> editTake(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long takeId,
            @Valid @RequestBody PostCommentDTO body) {
        return ResponseEntity.ok(socialService.editTake(me(ud), takeId, body.text()));
    }

    @DeleteMapping("/takes/{takeId}")
    public ResponseEntity<Void> deleteTake(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long takeId) {
        socialService.deleteTake(me(ud), takeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/takes/{takeId}/comments")
    public ResponseEntity<List<TakeCommentDTO>> listComments(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long takeId) {
        return ResponseEntity.ok(takeCommentService.list(me(ud), takeId));
    }

    @PostMapping("/takes/{takeId}/comments")
    public ResponseEntity<TakeCommentDTO> addComment(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long takeId,
            @Valid @RequestBody PostCommentDTO body) {
        return ResponseEntity.ok(
                takeCommentService.add(me(ud), takeId, body.text(), body.parentCommentId()));
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<TakeCommentDTO> editComment(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long commentId,
            @Valid @RequestBody PostCommentDTO body) {
        return ResponseEntity.ok(takeCommentService.edit(me(ud), commentId, body.text()));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserDetails ud, @PathVariable Long commentId) {
        takeCommentService.delete(me(ud), commentId);
        return ResponseEntity.noContent().build();
    }

    private Long me(UserDetails ud) {
        User user = authUtils.getUser(ud);
        return user.getId();
    }
}
