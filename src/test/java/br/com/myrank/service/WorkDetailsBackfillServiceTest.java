package br.com.myrank.service;

import br.com.myrank.domain.entity.Work;
import br.com.myrank.domain.enums.TableTemplate;
import br.com.myrank.dto.RefreshDetailsResultDTO;
import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import br.com.myrank.repository.WorkRepository;
import br.com.myrank.service.external.DeezerService;
import br.com.myrank.service.external.GoogleBooksService;
import br.com.myrank.service.external.MyAnimeListService;
import br.com.myrank.service.external.RawgService;
import br.com.myrank.service.external.TmdbService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkDetailsBackfillServiceTest {

    private final WorkRepository works = mock(WorkRepository.class);
    private final TmdbService tmdb = mock(TmdbService.class);
    private final GoogleBooksService books = mock(GoogleBooksService.class);
    private final DeezerService deezer = mock(DeezerService.class);
    private final WorkDetailsBackfillService service = new WorkDetailsBackfillService(works, tmdb,
            mock(RawgService.class), mock(MyAnimeListService.class), books, deezer, new ObjectMapper());

    private Work work(String title, TableTemplate template, Integer year, Map<String, Object> details) {
        Work w = new Work();
        w.setId((long) title.hashCode());
        w.setTitle(title);
        w.setTemplate(template);
        w.setScore(new BigDecimal("9"));
        w.setImageUrl("capa-original.jpg");
        w.setCreator("Criador Original");
        if (year != null) w.setReleaseDate(LocalDate.of(year, 1, 1));
        w.setDetails(new java.util.LinkedHashMap<>(details));
        return w;
    }

    private static ExternalSearchResultDTO result(String id, String title, String date) {
        return new ExternalSearchResultDTO(id, title, null, date);
    }

    private static ExternalWorkDetailsDTO fetched(Map<String, Object> details) {
        ExternalWorkDetailsDTO dto = new ExternalWorkDetailsDTO("Outro título", "outra-capa.jpg", "Outro criador", null, 0);
        dto.setDetails(details);
        return dto;
    }

    @Test
    void obraAntiga_ligaAoUnicoResultadoComMesmoTituloEAno_semMexerNoResto() {
        Work duna = work("Duna", TableTemplate.MOVIE, 2021, Map.of());
        when(works.findByUserId(1L)).thenReturn(List.of(duna));
        when(tmdb.searchMovies("Duna")).thenReturn(List.of(
                result("841", "Duna", "1984-12-14"),
                result("438631", "Duna", "2021-09-15"),
                result("999", "Duna: Parte Dois", "2024-02-27")));
        when(tmdb.getMovieDetails(438631L)).thenReturn(fetched(Map.of("genres", List.of("Ficção científica"))));

        RefreshDetailsResultDTO out = service.refreshAll(1L);

        assertThat(out.updated()).isEqualTo(1);
        assertThat(duna.getDetails()).containsEntry("genres", List.of("Ficção científica"))
                .containsEntry("provider", "tmdb").containsEntry("externalId", "438631");
        assertThat(duna.getTitle()).isEqualTo("Duna");
        assertThat(duna.getImageUrl()).isEqualTo("capa-original.jpg");
        assertThat(duna.getCreator()).isEqualTo("Criador Original");
        assertThat(duna.getScore()).isEqualByComparingTo("9");
        verify(works).save(duna);
    }

    @Test
    void obraAntiga_semData_eDoisResultadosIguais_ePulada() {
        Work batman = work("Batman", TableTemplate.MOVIE, null, Map.of());
        when(works.findByUserId(1L)).thenReturn(List.of(batman));
        when(tmdb.searchMovies("Batman")).thenReturn(List.of(
                result("268", "Batman", "1989-06-23"), result("2661", "Batman", "1966-07-30")));

        RefreshDetailsResultDTO out = service.refreshAll(1L);

        assertThat(out.updated()).isZero();
        assertThat(out.skipped()).containsExactly(
                new RefreshDetailsResultDTO.Skipped("Batman", "mais de um resultado com o mesmo título e ano"));
        verify(tmdb, never()).getMovieDetails(anyLong());
        verify(works, never()).save(any());
    }

    @Test
    void tituloIgualSemAcentoNemPontuacao_contaComoMesmoTitulo() {
        assertThat(WorkDetailsBackfillService.normalize("Steins;Gate")).isEqualTo(WorkDetailsBackfillService.normalize("steins gate"));
        assertThat(WorkDetailsBackfillService.normalize("Pokémon")).isEqualTo("pokemon");
    }

    @Test
    void obraComId_buscaDireto_semProcurarPeloTitulo() {
        Work livro = work("1984", TableTemplate.BOOK, 1949,
                Map.of("provider", "google-books", "externalId", "abc", "pages", 300));
        when(works.findByUserId(1L)).thenReturn(List.of(livro));
        when(books.getBookDetails("abc")).thenReturn(fetched(Map.of("pages", 328, "genres", List.of("Fiction"))));

        service.refreshAll(1L);

        assertThat(livro.getDetails()).containsEntry("pages", 328).containsEntry("genres", List.of("Fiction"));
        verify(books, never()).searchBooks(any());
    }

    @Test
    void musica_exigeOMesmoArtista() {
        Work thriller = work("Thriller", TableTemplate.MUSIC, null, Map.of());
        thriller.setCreator("Michael Jackson");
        when(works.findByUserId(1L)).thenReturn(List.of(thriller));
        ExternalSearchResultDTO cover = result("1", "Thriller", null);
        cover.setSubtitle("Banda Cover");
        ExternalSearchResultDTO original = result("2", "Thriller", null);
        original.setSubtitle("Michael Jackson");
        when(deezer.searchMusic("Thriller")).thenReturn(List.of(cover, original));
        when(deezer.getMusicDetails("2")).thenReturn(fetched(Map.of("album", "Thriller")));

        assertThat(service.refreshAll(1L).updated()).isEqualTo(1);
        assertThat(thriller.getDetails()).containsEntry("externalId", "2");
    }

    @Test
    void falhaNumaObra_naoParaAsOutras_ePersonalizadoFicaDeFora() {
        Work quebrada = work("Matrix", TableTemplate.MOVIE, 1999, Map.of());
        Work boa = work("Up", TableTemplate.MOVIE, 2009, Map.of());
        Work propria = work("Pizza de calabresa", TableTemplate.CUSTOM, null, Map.of());
        when(works.findByUserId(1L)).thenReturn(new ArrayList<>(List.of(quebrada, boa, propria)));
        when(tmdb.searchMovies("Matrix")).thenThrow(new ExternalServiceUnavailableException("fora do ar", null));

        RefreshDetailsResultDTO out = service.refreshAll(1L);

        assertThat(out.skipped()).extracting(RefreshDetailsResultDTO.Skipped::title).containsExactly("Matrix", "Up");
        assertThat(out.skipped().get(1).reason()).isEqualTo("título curto demais pra buscar");
    }
}
