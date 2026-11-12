package br.com.myrank.service.social;

import br.com.myrank.domain.entity.FeedEvent;
import br.com.myrank.domain.entity.User;
import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.FeedEventType;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.FeedItemDTO;
import br.com.myrank.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Avaliações entram no feed num card por pessoa por dia, nunca soltas. */
class SocialFeedRatedDayTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 20, 0);

    private final FollowRepository follows = mock(FollowRepository.class);
    private final FeedEventRepository events = mock(FeedEventRepository.class);
    private final WorkRepository works = mock(WorkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SocialService service = new SocialService(
            follows, mock(FollowRequestRepository.class), events,
            mock(FeedReactionRepository.class), mock(TakeRepository.class), mock(TakeCommentRepository.class), works,
            users, mock(UserBadgeRepository.class), mock(BadgeRepository.class),
            mock(FeedEventService.class), mock(NotificationService.class),
            Clock.fixed(Instant.from(NOW.atOffset(ZoneOffset.UTC)), ZoneOffset.UTC));

    @Test
    void ratingsOfTheSameDayBecomeOneCardWithDistinctWorks() {
        when(follows.findFollowedIds(1L)).thenReturn(List.of(2L));
        when(events.findFeed(any(), anyBoolean(), eq(FeedEventType.TAKE), any())).thenReturn(List.of());
        // dia 9: obra 10 avaliada duas vezes + obra 11; dia 8: obra 12
        List<FeedEvent> ratings = List.of(
                rating(103, 2L, 10L, NOW.minusHours(1)),
                rating(102, 2L, 11L, NOW.minusHours(2)),
                rating(101, 2L, 10L, NOW.minusHours(3)),
                rating(100, 2L, 12L, NOW.minusDays(1)));
        when(events.findRatingsBetween(any(), anyBoolean(), any(), any(), any(), any())).thenReturn(ratings);
        when(works.findAllById(any())).thenReturn(List.of(work(10L), work(11L), work(12L)));
        User marina = new User();
        marina.setId(2L);
        marina.setUsername("marina");
        when(users.findAllById(Set.of(2L))).thenReturn(List.of(marina));

        List<FeedItemDTO> feed = service.getFeed(1L, 0, 20);

        assertThat(feed).extracting(FeedItemDTO::type).containsExactly("RATED_DAY", "RATED_DAY");
        assertThat(feed.get(0).workCount()).isEqualTo(2);
        assertThat(feed.get(0).works()).extracting(w -> w.id()).containsExactly(10L, 11L);
        assertThat(feed.get(0).id()).isEqualTo(-103L);
        assertThat(feed.get(1).workCount()).isEqualTo(1);
    }

    @Test
    void ratingsOnlyLookBackThirtyDays() {
        when(follows.findFollowedIds(1L)).thenReturn(List.of());
        when(events.findFeed(any(), anyBoolean(), eq(FeedEventType.TAKE), any())).thenReturn(List.of());

        service.getFeed(1L, 0, 20);

        verify(events).findRatingsBetween(any(), eq(true), any(), eq(NOW.minusDays(30)), any(), any());
    }

    @Test
    void followingOnlyLeavesStrangersOut() {
        when(follows.findFollowedIds(1L)).thenReturn(List.of(2L));
        when(events.findFeed(any(), anyBoolean(), eq(FeedEventType.TAKE), any())).thenReturn(List.of());

        service.getFeed(1L, 0, 20, false);

        verify(events).findFeed(any(), eq(false), eq(FeedEventType.TAKE), any());
        verify(events).findRatingsBetween(any(), eq(false), any(), any(), any(), any());
    }

    private static FeedEvent rating(long id, long userId, long workId, LocalDateTime at) {
        FeedEvent e = mock(FeedEvent.class);
        when(e.getId()).thenReturn(id);
        when(e.getUserId()).thenReturn(userId);
        when(e.getWorkId()).thenReturn(workId);
        when(e.getType()).thenReturn(FeedEventType.RATED);
        when(e.getCreatedAt()).thenReturn(at);
        return e;
    }

    private static Work work(long id) {
        Work w = new Work();
        w.setId(id);
        w.setTitle("obra " + id);
        w.setTemplate(TableTemplate.MOVIE);
        w.setScore(new BigDecimal("8.5"));
        return w;
    }
}
