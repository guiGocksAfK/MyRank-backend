package br.com.myrank.controller;

import br.com.myrank.dto.external.ExternalSearchResultDTO;
import br.com.myrank.dto.external.ExternalWorkDetailsDTO;
import br.com.myrank.exception.ExternalApiExceptionHandler;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import br.com.myrank.service.external.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExternalMusicSearchControllerTest {

    private final DeezerService deezerService = mock(DeezerService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExternalSearchController(
            mock(TmdbService.class), mock(RawgService.class), mock(MyAnimeListService.class),
            mock(GoogleBooksService.class), mock(ShowcaseService.class), deezerService))
            .setControllerAdvice(new ExternalApiExceptionHandler()).build();

    @ParameterizedTest
    @ValueSource(strings = {"music", "albums"})
    void buscasUsamContratoCompartilhadoDeSugestoes(String route) throws Exception {
        var result = List.of(new ExternalSearchResultDTO("itunes:900", "Título",
                "https://img.test/cover.jpg", "2020-01-02"));
        if (route.equals("music")) when(deezerService.searchMusic("consulta")).thenReturn(result);
        else when(deezerService.searchAlbums("consulta")).thenReturn(result);

        mvc.perform(get("/api/external/search/" + route).param("query", "consulta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId").value("itunes:900"))
                .andExpect(jsonPath("$[0].title").value("Título"))
                .andExpect(jsonPath("$[0].posterUrl").value("https://img.test/cover.jpg"))
                .andExpect(jsonPath("$[0].releaseDate").value("2020-01-02"));
        if (route.equals("music")) verify(deezerService).searchMusic("consulta");
        else verify(deezerService).searchAlbums("consulta");
    }

    @ParameterizedTest
    @ValueSource(strings = {"music", "albums"})
    void detalhesAceitamIdDaReserva_eUsamContratoCompartilhado(String route) throws Exception {
        var details = new ExternalWorkDetailsDTO("Título", "https://img.test/cover.jpg",
                "Artista", "2020-01-02", 4);
        if (route.equals("music")) when(deezerService.getMusicDetails("itunes:900")).thenReturn(details);
        else when(deezerService.getAlbumDetails("itunes:900")).thenReturn(details);

        mvc.perform(get("/api/external/" + route + "/itunes:900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Título"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.test/cover.jpg"))
                .andExpect(jsonPath("$.creator").value("Artista"))
                .andExpect(jsonPath("$.releaseDate").value("2020-01-02"))
                .andExpect(jsonPath("$.timeMinutes").value(4));
        if (route.equals("music")) verify(deezerService).getMusicDetails("itunes:900");
        else verify(deezerService).getAlbumDetails("itunes:900");
    }

    @ParameterizedTest
    @ValueSource(strings = {"music", "albums"})
    void buscaExigeParametroQuery(String route) throws Exception {
        mvc.perform(get("/api/external/search/" + route)).andExpect(status().isBadRequest());
        verifyNoInteractions(deezerService);
    }

    @Test
    void indisponibilidadeDosDetalhesSegueTratamentoExistenteCom503() throws Exception {
        when(deezerService.getMusicDetails("42")).thenThrow(
                new ExternalServiceUnavailableException("Serviços indisponíveis.", new RuntimeException()));
        mvc.perform(get("/api/external/music/42"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Serviços indisponíveis."));
    }
}
