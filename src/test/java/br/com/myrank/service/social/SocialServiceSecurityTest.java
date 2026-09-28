package br.com.myrank.service.social;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SocialServiceSecurityTest {
    private final FollowRepository follows = mock(FollowRepository.class);
    private final FeedEventRepository events = mock(FeedEventRepository.class);
    private final FeedReactionRepository reactions = mock(FeedReactionRepository.class);
    private final WorkRepository works = mock(WorkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SocialService service = new SocialService(
            follows, mock(FollowRequestRepository.class), events,
            reactions, mock(TakeRepository.class), mock(TakeCommentRepository.class), works,
            users, mock(UserBadgeRepository.class), mock(BadgeRepository.class),
            mock(FeedEventService.class), mock(NotificationService.class));

    @Test
    void suggestionsDoNotRevealPrivateWorkStatistics() {
        User privateUser = new User();
        privateUser.setId(2L);
        privateUser.setUsername("privado");
        privateUser.setPublic(false);
        when(users.findSuggestions(eq(1L), any(), any())).thenReturn(List.of(privateUser));

        var card = service.getSuggestions(1L).get(0);

        assertThat(card.worksCount()).isZero();
        assertThat(card.avgScore()).isZero();
        verify(works, never()).findByUserId(2L);
    }

    @Test
    void outsiderCannotReactToPrivateTake() {
        FeedEvent event = mock(FeedEvent.class);
        when(event.getType()).thenReturn(FeedEventType.TAKE);
        when(event.getUserId()).thenReturn(2L);
        when(events.findById(77L)).thenReturn(Optional.of(event));
        User privateUser = new User();
        privateUser.setId(2L);
        privateUser.setPublic(false);
        when(users.findById(2L)).thenReturn(Optional.of(privateUser));

        assertThatThrownBy(() -> service.react(1L, 77L, "up"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reactions);
    }

    @Test
    void followerCanStillReactToPrivateTake() {
        FeedEvent event = mock(FeedEvent.class);
        when(event.getType()).thenReturn(FeedEventType.TAKE);
        when(event.getUserId()).thenReturn(2L);
        when(events.findById(77L)).thenReturn(Optional.of(event));
        User privateUser = new User();
        privateUser.setId(2L);
        privateUser.setPublic(false);
        when(users.findById(2L)).thenReturn(Optional.of(privateUser));
        when(follows.existsByFollowerIdAndFollowedId(1L, 2L)).thenReturn(true);

        service.react(1L, 77L, "up");

        verify(reactions).save(any());
    }

}
