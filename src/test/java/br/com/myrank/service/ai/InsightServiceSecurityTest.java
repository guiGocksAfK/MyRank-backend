package br.com.myrank.service.ai;

import br.com.myrank.domain.entity.Work;
import br.com.myrank.dto.insight.InsightGenerateRequestDTO;
import br.com.myrank.repository.AiInsightRepository;
import br.com.myrank.repository.WorkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InsightServiceSecurityTest {
    private final WorkRepository works = mock(WorkRepository.class);
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final AiUsageService usage = mock(AiUsageService.class);
    private final InsightService service = new InsightService(
            works, mock(AiInsightRepository.class), gemini, usage, new ObjectMapper());

    private void oneWork() {
        Work work = new Work();
        work.setId(10L);
        work.setTitle("Obra");
        work.setScore(BigDecimal.TEN);
        work.setFinalScore(BigDecimal.TEN);
        when(works.findByUserIdOrderByFinalScoreDesc(1L)).thenReturn(List.of(work));
        when(gemini.model()).thenReturn("teste");
    }

    @Test
    void exhaustedQuotaDoesNotCallProvider() {
        oneWork();
        when(usage.reserve(1L)).thenThrow(new IllegalArgumentException("Limite"));

        assertThatThrownBy(() -> service.generate(1L, new InsightGenerateRequestDTO(List.of(), true)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gemini, never()).analyze(anyString(), anyString());
    }

    @Test
    void providerFailureReturnsReservation() {
        oneWork();
        var reservation = new AiUsageService.Reservation(LocalDateTime.now(), 14);
        when(usage.reserve(1L)).thenReturn(reservation);
        when(gemini.analyze(anyString(), anyString())).thenThrow(new IllegalStateException("Provedor indisponível"));

        assertThatThrownBy(() -> service.generate(1L, new InsightGenerateRequestDTO(List.of(), true)))
                .isInstanceOf(IllegalStateException.class);
        verify(usage).release(1L, reservation);
    }
}
