package br.com.myrank.service.external;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.dto.external.MalAnimeNodeDTO;
import br.com.myrank.dto.external.MalListResponseDTO;
import br.com.myrank.dto.external.MalMangaNodeDTO;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MyAnimeListServiceTest {

    private static final String CLIENT_ID = "client-id-de-teste";
    private static final String SEARCH_FIELDS = "id,title,main_picture,start_date";
    private static final String MANGA_URL =
            "https://api.myanimelist.net/v2/manga/13?fields=id,title,main_picture,start_date,authors,num_volumes,status,genres";
    private static final String ANIME_URL = "https://api.myanimelist.net/v2/anime/13?fields="
            + "id,title,main_picture,start_date,num_episodes,average_episode_duration,studios,media_type,status,genres";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final MyAnimeListService service = new MyAnimeListService(restTemplate, CLIENT_ID);
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"anime", "manga"})
    void buscaMantemCamposMapeamentoClientId_eLimiteDeVinte(String resource) throws Exception {
        String url = searchUrl(resource, "Naruto");
        respond(url, MalListResponseDTO.class, mapper.readValue("""
                {"data":[{"node":{"id":13,"title":"Naruto","start_date":"1999-09-21",
                 "main_picture":{"large":"https://img.test/large.jpg","medium":"https://img.test/medium.jpg"}}},
                 {"node":{"id":14,"title":"Outra obra","start_date":"2000-01",
                  "main_picture":{"medium":"https://img.test/fallback.jpg"}}},
                 {"node":null},{"node":{"title":"Sem ID"}}],"paging":{"next":"ignorado"}}
                """, MalListResponseDTO.class));

        List<ExternalSearchResultDTO> results = search(resource, " Naruto ");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getExternalId()).isEqualTo("13");
        assertThat(results.get(0).getTitle()).isEqualTo("Naruto");
        assertThat(results.get(0).getPosterUrl()).isEqualTo("https://img.test/large.jpg");
        assertThat(results.get(0).getReleaseDate()).isEqualTo("1999-09-21");
        assertThat(results.get(1).getPosterUrl()).isEqualTo("https://img.test/fallback.jpg");
        assertThat(results.get(1).getReleaseDate()).isEqualTo("2000-01-01");
        verifyRequest(url, MalListResponseDTO.class);
        verifyNoMoreInteractions(restTemplate);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a", "ab", " ab "})
    void consultasCurtasOuAusentesNaoChamamApiEmAnimeNemManga(String query) {
        assertThat(service.searchAnime(query)).isEmpty();
        assertThat(service.searchManga(query)).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"anime", "manga"})
    void buscaSemRespostaOuSemDadosDegradaParaVazio(String resource) {
        respond(searchUrl(resource, "Naruto"), MalListResponseDTO.class, null);
        assertThat(search(resource, "Naruto")).isEmpty();
        respond(searchUrl(resource, "Naruto"), MalListResponseDTO.class, new MalListResponseDTO());
        assertThat(search(resource, "Naruto")).isEmpty();
        MalListResponseDTO empty = new MalListResponseDTO();
        empty.setData(List.of());
        respond(searchUrl(resource, "Naruto"), MalListResponseDTO.class, empty);
        assertThat(search(resource, "Naruto")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"anime", "manga"})
    void buscaComTimeoutOuErroDeServidorMantemListaVazia(String resource) {
        when(restTemplate.exchange(eq(searchUrl(resource, "Naruto")), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(MalListResponseDTO.class)))
                .thenThrow(new ResourceAccessException("MAL fora do ar"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(search(resource, "Naruto")).isEmpty();
        assertThat(search(resource, "Naruto")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"anime,animes", "manga,mangás"})
    void buscaComErroDeClienteMantemExcecaoMensagem_eCausa(String resource, String worksName) {
        RestClientException cause = new HttpClientErrorException(HttpStatus.FORBIDDEN);
        when(restTemplate.exchange(eq(searchUrl(resource, "Naruto")), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(MalListResponseDTO.class))).thenThrow(cause);
        assertThatThrownBy(() -> search(resource, "Naruto"))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Não foi possível buscar " + worksName + " agora. O MyAnimeList pode estar instável ou limitando requisições. Tente novamente em instantes.")
                .hasCause(cause);
    }

    @Test
    void detalhesDeMangaUsamAutoresDataImagem_eDuracaoZero() throws Exception {
        respond(MANGA_URL, MalMangaNodeDTO.class, mapper.readValue("""
                {"id":13,"title":"Death Note","start_date":"2003-12-01",
                 "main_picture":{"large":"https://img.test/manga.jpg"},
                 "authors":[{"node":{"id":1,"first_name":"Tsugumi","last_name":"Ohba"},"role":"Story"},
                            {"node":{"id":2,"first_name":"Takeshi","last_name":"Obata"},"role":"Art"}],
                 "num_episodes":37,"average_episode_duration":1440,"num_chapters":108,"num_volumes":12,
                 "status":"finished"}
                """, MalMangaNodeDTO.class));

        ExternalWorkDetailsDTO details = service.getMangaDetails(13L);
        assertThat(details.getDetails()).isEqualTo(java.util.Map.of("volumes", 12, "status", "finished"));

        assertThat(details.getTitle()).isEqualTo("Death Note");
        assertThat(details.getImageUrl()).isEqualTo("https://img.test/manga.jpg");
        assertThat(details.getCreator()).isEqualTo("Tsugumi Ohba, Takeshi Obata");
        assertThat(details.getReleaseDate()).isEqualTo("2003-12-01");
        assertThat(details.getTimeMinutes()).isZero();
        verifyRequest(MANGA_URL, MalMangaNodeDTO.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null]", "[{\"node\":null}]",
            "[{\"node\":{\"first_name\":\" \",\"last_name\":null}}]"})
    void autorAusenteOuSemNomeResultaEmCriadorNulo(String authors) throws Exception {
        respond(MANGA_URL, MalMangaNodeDTO.class, mapper.readValue(
                "{\"id\":13,\"title\":\"Mangá\",\"authors\":" + authors + "}", MalMangaNodeDTO.class));
        ExternalWorkDetailsDTO details = service.getMangaDetails(13L);
        assertThat(details.getCreator()).isNull();
        assertThat(details.getImageUrl()).isNull();
        assertThat(details.getReleaseDate()).isNull();
        assertThat(details.getTimeMinutes()).isZero();
    }

    @Test
    void campoDeAutoresOmitidoMantemCriadorNulo_eReaproveitaCapaEDataParcial() throws Exception {
        respond(MANGA_URL, MalMangaNodeDTO.class, mapper.readValue("""
                {"id":13,"title":"Mangá","start_date":"1989",
                 "main_picture":{"large":" ","medium":"https://img.test/medium.jpg"}}
                """, MalMangaNodeDTO.class));
        ExternalWorkDetailsDTO details = service.getMangaDetails(13L);
        assertThat(details.getCreator()).isNull();
        assertThat(details.getImageUrl()).isEqualTo("https://img.test/medium.jpg");
        assertThat(details.getReleaseDate()).isEqualTo("1989-01-01");
    }

    @Test
    void autoresComNomeParcialOuRepetidosNaoGeramSeparadoresVazios() throws Exception {
        respond(MANGA_URL, MalMangaNodeDTO.class, mapper.readValue("""
                {"id":13,"title":"Mangá","authors":[null,{"node":null},
                  {"node":{"first_name":" Kentarou ","last_name":" Miura "}},
                  {"node":{"first_name":"Kentarou","last_name":"Miura"}},
                  {"node":{"last_name":"CLAMP"}},{"node":{"first_name":"ONE"}},{"node":{}}]}
                """, MalMangaNodeDTO.class));
        assertThat(service.getMangaDetails(13L).getCreator()).isEqualTo("Kentarou Miura, CLAMP, ONE");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void idDeMangaInvalidoRetorna400SemChamadaExterna(Long id) {
        assertThatThrownBy(() -> service.getMangaDetails(id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(restTemplate);
    }

    @ParameterizedTest
    @CsvSource({"anime,anime", "manga,mangá"})
    void detalhesComTimeoutOu5xxMantemErroExternoClaro(String resource, String workName) {
        String url = resource.equals("anime") ? ANIME_URL : MANGA_URL;
        Class<? extends MalAnimeNodeDTO> type = resource.equals("anime") ? MalAnimeNodeDTO.class : MalMangaNodeDTO.class;
        ResourceAccessException timeout = new ResourceAccessException("MAL fora do ar");
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(type)))
                .thenThrow(timeout).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> details(resource, 13L))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Não foi possível buscar os detalhes do " + workName + " agora. O MyAnimeList pode estar instável — tente novamente em instantes.")
                .hasCause(timeout);
        assertThatThrownBy(() -> details(resource, 13L)).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @ParameterizedTest
    @CsvSource({"anime,anime", "manga,mangá"})
    void detalhesNulosOuSemTituloMantemErroExternoClaro(String resource, String workName) {
        if (resource.equals("anime")) respond(ANIME_URL, MalAnimeNodeDTO.class, null);
        else respond(MANGA_URL, MalMangaNodeDTO.class, null);
        assertThatThrownBy(() -> details(resource, 13L))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Não foi possível buscar os detalhes do " + workName + " agora. Tente novamente em instantes.");
        if (resource.equals("anime")) respond(ANIME_URL, MalAnimeNodeDTO.class, new MalAnimeNodeDTO());
        else respond(MANGA_URL, MalMangaNodeDTO.class, new MalMangaNodeDTO());
        assertThatThrownBy(() -> details(resource, 13L)).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void clientIdAusenteMantemBuscasVazias_eDetalhesCom503(String clientId) {
        MyAnimeListService unconfigured = new MyAnimeListService(restTemplate, clientId);
        assertThat(unconfigured.searchAnime("Naruto")).isEmpty();
        assertThat(unconfigured.searchManga("Naruto")).isEmpty();
        assertThat(unconfigured.getShowcasePosters()).isEmpty();
        assertThatThrownBy(() -> unconfigured.getAnimeDetails(13L))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("A integração com o MyAnimeList não está configurada.");
        assertThatThrownBy(() -> unconfigured.getMangaDetails(13L))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("A integração com o MyAnimeList não está configurada.");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void detalhesDeAnimePreservamTituloEstudiosDataImagem_eDuracaoEstimada() throws Exception {
        respond(ANIME_URL, MalAnimeNodeDTO.class, mapper.readValue("""
                {"id":13,"title":"Anime","start_date":"2006-10",
                 "main_picture":{"large":"https://img.test/anime.jpg"},
                 "studios":[{"id":1,"name":"Madhouse"},{"id":2,"name":"Outro estúdio"}],
                 "num_episodes":37,"average_episode_duration":1439}
                """, MalAnimeNodeDTO.class));
        ExternalWorkDetailsDTO details = service.getAnimeDetails(13L);
        assertThat(details.getTitle()).isEqualTo("Anime");
        assertThat(details.getCreator()).isEqualTo("Madhouse, Outro estúdio");
        assertThat(details.getReleaseDate()).isEqualTo("2006-10-01");
        assertThat(details.getImageUrl()).isEqualTo("https://img.test/anime.jpg");
        assertThat(details.getTimeMinutes()).isEqualTo(37 * 23);
        verifyRequest(ANIME_URL, MalAnimeNodeDTO.class);
    }

    @ParameterizedTest
    @CsvSource({"0,1440,24", "-1,1440,24", "12,0,0", "12,59,0"})
    void animePreservaCalculoComEpisodiosOuDuracaoIndefinidos(int episodes, int seconds, int minutes) {
        MalAnimeNodeDTO anime = new MalAnimeNodeDTO();
        anime.setTitle("Anime");
        anime.setNumEpisodes(episodes);
        anime.setAverageEpisodeDuration(seconds);
        respond(ANIME_URL, MalAnimeNodeDTO.class, anime);
        assertThat(service.getAnimeDetails(13L).getTimeMinutes()).isEqualTo(minutes);
    }

    @Test
    void showcaseDeAnimeMantemUrlImagens_eDegradacaoEmFalhas() throws Exception {
        String url = "https://api.myanimelist.net/v2/anime/ranking?ranking_type=bypopularity&limit=20&fields=main_picture";
        respond(url, MalListResponseDTO.class, mapper.readValue("""
                {"data":[{"node":{"main_picture":{"large":"https://img.test/large.jpg"}}},
                 {"node":{"main_picture":{"medium":"https://img.test/medium.jpg"}}},{"node":null},{"node":{}}]}
                """, MalListResponseDTO.class));
        assertThat(service.getShowcasePosters()).containsExactly("https://img.test/large.jpg", "https://img.test/medium.jpg");
        verifyRequest(url, MalListResponseDTO.class);
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(MalListResponseDTO.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        assertThat(service.getShowcasePosters()).isEmpty();
    }

    private String searchUrl(String resource, String query) {
        return "https://api.myanimelist.net/v2/" + resource + "?q=" + query + "&limit=20&fields=" + SEARCH_FIELDS;
    }

    private List<ExternalSearchResultDTO> search(String resource, String query) {
        return resource.equals("anime") ? service.searchAnime(query) : service.searchManga(query);
    }

    private ExternalWorkDetailsDTO details(String resource, Long id) {
        return resource.equals("anime") ? service.getAnimeDetails(id) : service.getMangaDetails(id);
    }

    private <T> void respond(String url, Class<T> type, T body) {
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(type)))
                .thenReturn(ResponseEntity.ok(body));
    }

    @SuppressWarnings("rawtypes")
    private <T> void verifyRequest(String url, Class<T> type) {
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(url), eq(HttpMethod.GET), request.capture(), eq(type));
        assertThat(request.getValue().getHeaders().getFirst("X-MAL-CLIENT-ID")).isEqualTo(CLIENT_ID);
        assertThat(request.getValue().getHeaders().getFirst("accept")).isEqualTo("application/json");
        assertThat(request.getValue().getBody()).isNull();
    }
}
