package br.com.myrank.service.social;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.entity.Notification;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.domain.enums.NotificationType;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
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
            mock(FeedEventService.class), mock(NotificationService.class), Clock.systemUTC());

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

    @Test
    void notificationDoesNotRevealWorkFromLockedAccount() {
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        NotificationService notifService = new NotificationService(
                notifRepo, reactions, events, follows, works, users, mock(ConversationRepository.class));

        Notification n = new Notification();
        n.setUserId(1L);
        n.setType(NotificationType.TAKE);
        n.setActorId(2L);
        n.setFeedEventId(77L);
        when(notifRepo.findByUserIdOrderByUpdatedAtDesc(eq(1L), any())).thenReturn(List.of(n));

        User lockedAuthor = new User();
        lockedAuthor.setId(2L);
        lockedAuthor.setUsername("trancou");
        lockedAuthor.setPublic(false);
        when(users.findAllById(any())).thenReturn(List.of(lockedAuthor));

        FeedEvent ev = mock(FeedEvent.class);
        when(ev.getId()).thenReturn(77L);
        when(ev.getUserId()).thenReturn(2L);
        when(ev.getType()).thenReturn(FeedEventType.TAKE);
        when(ev.getWorkId()).thenReturn(99L);
        when(events.findAllById(any())).thenReturn(List.of(ev));

        Work work = new Work();
        work.setId(99L);
        work.setTitle("Obra Secreta");
        work.setTemplate(TableTemplate.MOVIE);
        work.setScore(BigDecimal.TEN);
        when(works.findAllById(any())).thenReturn(List.of(work));
        when(follows.findFollowedIds(1L)).thenReturn(List.of());

        var dto = notifService.list(1L, 0, 10).get(0);

        assertThat(dto.work()).isNull();
        assertThat(dto.message()).doesNotContain("Obra Secreta");
    }

}
