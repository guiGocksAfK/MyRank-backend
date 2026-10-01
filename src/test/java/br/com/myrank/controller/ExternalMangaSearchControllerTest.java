package br.com.myrank.controller;

import br.com.myrank.dto.external.MalListResponseDTO;
import br.com.myrank.dto.external.MalMangaNodeDTO;
import br.com.myrank.exception.ExternalApiExceptionHandler;
import br.com.myrank.service.external.DeezerService;
import br.com.myrank.service.external.GoogleBooksService;
import br.com.myrank.service.external.MyAnimeListService;
import br.com.myrank.service.external.RawgService;
import br.com.myrank.service.external.ShowcaseService;
import br.com.myrank.service.external.TmdbService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExternalMangaSearchControllerTest {

    private static final String SEARCH_URL =
            "https://api.myanimelist.net/v2/manga?q=Naruto&limit=20&fields=id,title,main_picture,start_date";
    private static final String DETAILS_URL =
            "https://api.myanimelist.net/v2/manga/13?fields=id,title,main_picture,start_date,authors";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExternalSearchController(
            mock(TmdbService.class), mock(RawgService.class), new MyAnimeListService(restTemplate, "client-id-de-teste"),
            mock(GoogleBooksService.class), mock(ShowcaseService.class), mock(DeezerService.class)))
            .setControllerAdvice(new ExternalApiExceptionHandler()).build();

    @Test
    void buscaDeMangaDevolveContratoExistenteDeSugestao() throws Exception {
        when(restTemplate.exchange(eq(SEARCH_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(MalListResponseDTO.class)))
                .thenReturn(ResponseEntity.ok(mapper.readValue("""
                        {"data":[{"node":{"id":13,"title":"Naruto","start_date":"1999-09-21",
                         "main_picture":{"large":"https://img.test/manga.jpg"}}}]}
                        """, MalListResponseDTO.class)));

        mvc.perform(get("/api/external/search/manga").param("query", "Naruto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId").value("13"))
                .andExpect(jsonPath("$[0].title").value("Naruto"))
                .andExpect(jsonPath("$[0].posterUrl").value("https://img.test/manga.jpg"))
                .andExpect(jsonPath("$[0].releaseDate").value("1999-09-21"));
    }

    @Test
    void detalhesDeMangaDevolvemContratoExistenteComAutores_eTempoZero() throws Exception {
        respondToDetails("""
                {"id":13,"title":"Mangá","start_date":"2003-12-01","main_picture":{"large":"https://img.test/manga.jpg"},
                 "authors":[{"node":{"first_name":"Tsugumi","last_name":"Ohba"}},
                            {"node":{"first_name":"Takeshi","last_name":"Obata"}}]}
                """);

        mvc.perform(get("/api/external/manga/13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mangá"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.test/manga.jpg"))
                .andExpect(jsonPath("$.creator").value("Tsugumi Ohba, Takeshi Obata"))
                .andExpect(jsonPath("$.releaseDate").value("2003-12-01"))
                .andExpect(jsonPath("$.timeMinutes").value(0));
    }

    @Test
    void autorAusenteRetornaCriadorNuloSemFalhar() throws Exception {
        respondToDetails("{\"id\":13,\"title\":\"Mangá\"}");
        mvc.perform(get("/api/external/manga/13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creator").value(nullValue()))
                .andExpect(jsonPath("$.timeMinutes").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9999999999999999999999"})
    void idInvalidoRetorna400SemChamadaAoMyAnimeList(String id) throws Exception {
        mvc.perform(get("/api/external/manga/" + id)).andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void buscaExigeParametroQuery() throws Exception {
        mvc.perform(get("/api/external/search/manga")).andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void myAnimeListForaDoArRetornaSugestoesVazias() throws Exception {
        when(restTemplate.exchange(eq(SEARCH_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(MalListResponseDTO.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(get("/api/external/search/manga").param("query", "Naruto"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void myAnimeListForaDoArNosDetalhesUsaTratamentoExistenteCom503() throws Exception {
        when(restTemplate.exchange(eq(DETAILS_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(MalMangaNodeDTO.class)))
                .thenThrow(new ResourceAccessException("MAL fora do ar"));
        mvc.perform(get("/api/external/manga/13"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Não foi possível buscar os detalhes do mangá agora. O MyAnimeList pode estar instável — tente novamente em instantes."));
    }

    private void respondToDetails(String json) throws Exception {
        when(restTemplate.exchange(eq(DETAILS_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(MalMangaNodeDTO.class)))
                .thenReturn(ResponseEntity.ok(mapper.readValue(json, MalMangaNodeDTO.class)));
    }
}
