package br.com.myrank.service.external;

import br.com.myrank.dto.external.GoogleBookItemDTO;
import br.com.myrank.dto.external.MalAnimeNodeDTO;
import br.com.myrank.dto.external.RawgGameDetailsDTO;
import br.com.myrank.dto.external.TmdbMovieDetailsDTO;
import br.com.myrank.dto.external.TmdbTvDetailsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fase 4: o que cada API já mandava e agora vai pro `details` do card (e gêneros pra IA). */
class CardDetailsMappingTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serie_temporadasSituacaoEGeneros() throws Exception {
        TmdbTvDetailsDTO tv = mapper.readValue("""
                {"id":1396,"name":"Breaking Bad","number_of_seasons":5,"status":"Ended",
                 "genres":[{"id":18,"name":"Drama"},{"id":80,"name":"Crime"}]}
                """, TmdbTvDetailsDTO.class);
        when(restTemplate.exchange(contains("/tv/1396"), eq(HttpMethod.GET), any(HttpEntity.class), eq(TmdbTvDetailsDTO.class)))
                .thenReturn(ResponseEntity.ok(tv));

        var details = new TmdbService(restTemplate, "token").getTvShowDetails(1396L).getDetails();
        assertThat(details).isEqualTo(Map.of("seasons", 5, "status", "Ended", "genres", List.of("Drama", "Crime")));
    }

    @Test
    void filme_soGeneros_porqueAnoEDuracaoJaVaoNosCamposNormais() throws Exception {
        TmdbMovieDetailsDTO movie = mapper.readValue("""
                {"id":157336,"title":"Interestelar","runtime":169,"genres":[{"id":878,"name":"Ficção científica"}]}
                """, TmdbMovieDetailsDTO.class);
        when(restTemplate.exchange(contains("/movie/157336"), eq(HttpMethod.GET), any(HttpEntity.class), eq(TmdbMovieDetailsDTO.class)))
                .thenReturn(ResponseEntity.ok(movie));

        var details = new TmdbService(restTemplate, "token").getMovieDetails(157336L).getDetails();
        assertThat(details).isEqualTo(Map.of("genres", List.of("Ficção científica")));
    }

    @Test
    void anime_episodiosTipoSituacaoEGeneros() throws Exception {
        MalAnimeNodeDTO anime = mapper.readValue("""
                {"id":1535,"title":"Death Note","num_episodes":37,"media_type":"tv","status":"finished_airing",
                 "genres":[{"id":7,"name":"Mystery"},{"id":37,"name":"Supernatural"}]}
                """, MalAnimeNodeDTO.class);
        when(restTemplate.exchange(contains("/anime/1535"), eq(HttpMethod.GET), any(HttpEntity.class), eq(MalAnimeNodeDTO.class)))
                .thenReturn(ResponseEntity.ok(anime));

        var details = new MyAnimeListService(restTemplate, "client").getAnimeDetails(1535L).getDetails();
        assertThat(details).isEqualTo(Map.of("episodes", 37, "mediaType", "tv", "status", "finished_airing",
                "genres", List.of("Mystery", "Supernatural")));
    }

    @Test
    void jogo_soGeneros() throws Exception {
        RawgGameDetailsDTO game = mapper.readValue("""
                {"id":326243,"name":"Elden Ring","genres":[{"id":4,"name":"Action"},{"id":5,"name":"RPG"},{"id":5,"name":"RPG"}]}
                """, RawgGameDetailsDTO.class);
        when(restTemplate.getForObject(contains("/games/326243"), eq(RawgGameDetailsDTO.class))).thenReturn(game);

        var details = new RawgService(restTemplate, "key").getGameDetails(326243L).getDetails();
        assertThat(details).isEqualTo(Map.of("genres", List.of("Action", "RPG"))); // repetido sai
    }

    @Test
    void livro_paginasECategorias_eSemDadoNaoInventaNada() throws Exception {
        GoogleBookItemDTO book = mapper.readValue("""
                {"id":"abc","volumeInfo":{"title":"Dom Casmurro","pageCount":256,"categories":["Fiction"]}}
                """, GoogleBookItemDTO.class);
        GoogleBookItemDTO bare = mapper.readValue("""
                {"id":"xyz","volumeInfo":{"title":"Sem dados","pageCount":0}}
                """, GoogleBookItemDTO.class);
        when(restTemplate.getForObject(contains("/abc"), eq(GoogleBookItemDTO.class))).thenReturn(book);
        when(restTemplate.getForObject(contains("/xyz"), eq(GoogleBookItemDTO.class))).thenReturn(bare);

        GoogleBooksService service = new GoogleBooksService(restTemplate, "");
        assertThat(service.getBookDetails("abc").getDetails()).isEqualTo(Map.of("pages", 256, "genres", List.of("Fiction")));
        assertThat(service.getBookDetails("xyz").getDetails()).isEmpty();
    }
}
