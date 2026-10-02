package br.com.myrank.service.external;

import br.com.myrank.exception.ExternalServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeezerServiceTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final MusicSearchCache cache = new MusicSearchCache();
    private final DeezerService service = new DeezerService(
            restTemplate, new ItunesService(restTemplate, cache), cache);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buscaFaixasUsaApiPublica_eReaproveitaCacheSemExporObjetosMutaveis() throws Exception {
        String url = "https://api.deezer.com/search/track?q=Discovery&limit=20";
        respond(url, """
                {"data":[{"id":42,"title":"One More Time","release_date":"2000-11-13",
                  "artist":{"name":"Daft Punk"},"album":{"cover_big":"https://img.test/cover.jpg"}}]}
                """);

        var result = service.searchMusic(" Discovery ").get(0);
        assertThat(result.getExternalId()).isEqualTo("42");
        assertThat(result.getTitle()).isEqualTo("One More Time");
        assertThat(result.getPosterUrl()).isEqualTo("https://img.test/cover.jpg");
        assertThat(result.getReleaseDate()).isEqualTo("2000-11-13");
        result.setTitle("Alterado pelo consumidor");
        assertThat(service.searchMusic("Discovery").get(0).getTitle()).isEqualTo("One More Time");
        verify(restTemplate, times(1)).getForObject(URI.create(url), JsonNode.class);
        verifyNoMoreInteractions(restTemplate);
    }

    @Test
    void buscaAlbunsUsaEndpointProprio_eToleraMetadadosAusentes() throws Exception {
        respond("https://api.deezer.com/search/album?q=Discovery&limit=20", """
                {"data":[{"id":50,"title":"Discovery","cover_medium":"https://img.test/album.jpg"},
                  {"id":0,"title":"Inválido"},{"id":51}]}
                """);
        var results = service.searchAlbums("Discovery");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("50");
        assertThat(results.get(0).getPosterUrl()).isEqualTo("https://img.test/album.jpg");
        assertThat(results.get(0).getReleaseDate()).isNull();
    }

    @Test
    void detalhesDaFaixaMapeiamArtistaDataImagem_eSegundosParaMinutos() throws Exception {
        respond("https://api.deezer.com/track/42", track());
        var details = service.getMusicDetails("42");
        assertThat(details.getTitle()).isEqualTo("One More Time");
        assertThat(details.getCreator()).isEqualTo("Daft Punk");
        assertThat(details.getImageUrl()).isEqualTo("https://img.test/cover.jpg");
        assertThat(details.getReleaseDate()).isEqualTo("2000-11-13");
        assertThat(details.getTimeMinutes()).isEqualTo(5);
        details.setCreator("Alterado");
        assertThat(service.getMusicDetails("42").getCreator()).isEqualTo("Daft Punk");
        verify(restTemplate, times(1)).getForObject(URI.create("https://api.deezer.com/track/42"), JsonNode.class);
    }

    @Test
    void detalhesDaFaixaBuscamDataNoAlbumQuandoNecessario() throws Exception {
        respond("https://api.deezer.com/track/42", """
                {"id":42,"title":"Faixa","duration":179,"artist":{"name":"Artista"},"album":{"id":50}}
                """);
        respond("https://api.deezer.com/album/50", """
                {"id":50,"title":"Álbum","release_date":"2020-01-02"}
                """);
        var details = service.getMusicDetails("42");
        assertThat(details.getReleaseDate()).isEqualTo("2020-01-02");
        assertThat(details.getTimeMinutes()).isEqualTo(3);
    }

    @Test
    void detalhesDoAlbumUsamDuracaoTotalEmSegundos() throws Exception {
        respond("https://api.deezer.com/album/50", """
                {"id":50,"title":"Discovery","duration":3660,"release_date":"2001-03-12",
                 "artist":{"name":"Daft Punk"},"cover_big":"https://img.test/album.jpg"}
                """);
        var details = service.getAlbumDetails("50");
        assertThat(details.getTitle()).isEqualTo("Discovery");
        assertThat(details.getCreator()).isEqualTo("Daft Punk");
        assertThat(details.getImageUrl()).isEqualTo("https://img.test/album.jpg");
        assertThat(details.getReleaseDate()).isEqualTo("2001-03-12");
        assertThat(details.getTimeMinutes()).isEqualTo(61);
    }

    @Test
    void albumSemDuracaoTotalSomaFaixasAntesDeArredondar() throws Exception {
        respond("https://api.deezer.com/album/50", """
                {"id":50,"title":"Álbum","duration":-120,"tracks":{"data":[{"duration":89},{"duration":89}]}}
                """);
        assertThat(service.getAlbumDetails("50").getTimeMinutes()).isEqualTo(3);
    }

    @Test
    void deezerForaDoArCaiParaBuscaDeMusicasNoItunes() throws Exception {
        when(restTemplate.getForObject(URI.create("https://api.deezer.com/search/track?q=Discovery&limit=20"),
                JsonNode.class)).thenThrow(new ResourceAccessException("Deezer fora do ar"));
        respond("https://itunes.apple.com/search?term=Discovery&media=music&entity=song&country=BR&limit=20",
                """
                {"results":[{"kind":"song","trackId":900,"trackName":"One More Time","artistName":"Daft Punk",
                 "releaseDate":"2000-11-13T08:00:00Z","artworkUrl100":"https://img.test/itunes.jpg"}]}
                """);
        var result = service.searchMusic("Discovery").get(0);
        assertThat(result.getExternalId()).isEqualTo("itunes:900");
        assertThat(result.getReleaseDate()).isEqualTo("2000-11-13");
        assertThat(result.getPosterUrl()).isEqualTo("https://img.test/itunes.jpg");
    }

    @Test
    void erroDeezerNoCorpoHttp200CaiParaBuscaDeAlbunsNoItunes() throws Exception {
        respond("https://api.deezer.com/search/album?q=Discovery&limit=20",
                "{\"error\":{\"code\":4,\"message\":\"Quota\"}}");
        respond("https://itunes.apple.com/search?term=Discovery&media=music&entity=album&country=BR&limit=20",
                """
                {"results":[{"wrapperType":"collection","collectionType":"Album",
                 "collectionId":950,"collectionName":"Discovery","artistName":"Daft Punk"}]}
                """);
        assertThat(service.searchAlbums("Discovery").get(0).getExternalId()).isEqualTo("itunes:950");
    }

    @Test
    void buscaVaziaValidaNaoAcionaReserva() throws Exception {
        respond("https://api.deezer.com/search/track?q=ausente&limit=20", "{\"data\":[]}");
        assertThat(service.searchMusic("ausente")).isEmpty();
        verifyNoMoreInteractionsAfterDeezerSearch("https://api.deezer.com/search/track?q=ausente&limit=20");
    }

    @Test
    void buscaDevolveListaVaziaSeAsDuasBasesFalharem_eNaoGuardaErroNoCache() throws Exception {
        String deezerUrl = "https://api.deezer.com/search/track?q=Discovery&limit=20";
        when(restTemplate.getForObject(URI.create(deezerUrl), JsonNode.class))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(mapper.readTree("{\"data\":[]}"));
        when(restTemplate.getForObject(URI.create(
                "https://itunes.apple.com/search?term=Discovery&media=music&entity=song&country=BR&limit=20"),
                JsonNode.class)).thenThrow(new ResourceAccessException("iTunes fora do ar"));
        assertThat(service.searchMusic("Discovery")).isEmpty();
        assertThat(service.searchMusic("Discovery")).isEmpty();
        verify(restTemplate, times(2)).getForObject(URI.create(deezerUrl), JsonNode.class);
    }

    @Test
    void detalhesDeIdItunesUsamReservaDiretamente() throws Exception {
        respond("https://itunes.apple.com/lookup?id=900&entity=song&country=BR&limit=200", """
                {"results":[{"kind":"song","trackId":900,"trackName":"Faixa","trackTimeMillis":245000}]}
                """);
        assertThat(service.getMusicDetails("itunes:900").getTimeMinutes()).isEqualTo(4);
        verify(restTemplate).getForObject(URI.create(
                "https://itunes.apple.com/lookup?id=900&entity=song&country=BR&limit=200"), JsonNode.class);
        verifyNoMoreInteractions(restTemplate);
    }

    @Test
    void detalhesDeezerForaDoArEncontramAMesmaFaixaNoItunesPeloTituloEArtista() throws Exception {
        respond("https://api.deezer.com/search/track?q=Discovery&limit=20", "{\"data\":[" + track() + "]}");
        service.searchMusic("Discovery");
        when(restTemplate.getForObject(URI.create("https://api.deezer.com/track/42"), JsonNode.class))
                .thenThrow(new ResourceAccessException("Deezer fora do ar"));
        respond("https://itunes.apple.com/search?term=One%20More%20Time%20Daft%20Punk&media=music&entity=song&country=BR&limit=20",
                """
                {"results":[{"kind":"song","trackId":899,"trackName":"One More Time","artistName":"Outro artista"},
                 {"kind":"song","trackId":900,"trackName":"One More Time","artistName":"Daft Punk"}]}
                """);
        respond("https://itunes.apple.com/lookup?id=900&entity=song&country=BR&limit=200", """
                {"results":[{"kind":"song","trackId":900,"trackName":"One More Time",
                 "artistName":"Daft Punk","trackTimeMillis":320000}]}
                """);
        assertThat(service.getMusicDetails("42").getCreator()).isEqualTo("Daft Punk");
    }

    @Test
    void detalhesSemIdentidadeConhecidaNaoReutilizamIdDeezerComoIdItunes() {
        when(restTemplate.getForObject(URI.create("https://api.deezer.com/track/42"), JsonNode.class))
                .thenThrow(new ResourceAccessException("Deezer fora do ar"));
        assertThatThrownBy(() -> service.getMusicDetails("42"))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        verify(restTemplate).getForObject(URI.create("https://api.deezer.com/track/42"), JsonNode.class);
        verifyNoMoreInteractions(restTemplate);
    }

    @Test
    void detalhesDeAlbumDeezerForaDoArUsamAMesmaObraNoItunes() throws Exception {
        respond("https://api.deezer.com/search/album?q=Discovery&limit=20", """
                {"data":[{"id":50,"title":"Discovery","artist":{"name":"Daft Punk"}}]}
                """);
        service.searchAlbums("Discovery");
        when(restTemplate.getForObject(URI.create("https://api.deezer.com/album/50"), JsonNode.class))
                .thenThrow(new ResourceAccessException("Deezer fora do ar"));
        respond("https://itunes.apple.com/search?term=Discovery%20Daft%20Punk&media=music&entity=album&country=BR&limit=20", """
                {"results":[{"wrapperType":"collection","collectionType":"Album","collectionId":950,
                 "collectionName":"Discovery","artistName":"Daft Punk"}]}
                """);
        respond("https://itunes.apple.com/lookup?id=950&entity=song&country=BR&limit=200", """
                {"results":[{"wrapperType":"collection","collectionType":"Album","collectionId":950,
                 "collectionName":"Discovery","artistName":"Daft Punk"},
                 {"kind":"song","collectionId":950,"trackTimeMillis":245000}]}
                """);
        assertThat(service.getAlbumDetails("50").getTimeMinutes()).isEqualTo(4);
    }

    @Test
    void detalhesDaReservaSemCorrespondenciaOuForaDoArRetornamErroExterno() throws Exception {
        respond("https://api.deezer.com/search/track?q=Discovery&limit=20", "{\"data\":[" + track() + "]}");
        service.searchMusic("Discovery");
        when(restTemplate.getForObject(URI.create("https://api.deezer.com/track/42"), JsonNode.class))
                .thenThrow(new ResourceAccessException("Deezer fora do ar"));
        String url = "https://itunes.apple.com/search?term=One%20More%20Time%20Daft%20Punk&media=music&entity=song&country=BR&limit=20";
        when(restTemplate.getForObject(URI.create(url), JsonNode.class))
                .thenThrow(new ResourceAccessException("iTunes fora do ar"))
                .thenReturn(mapper.readTree("""
                        {"results":[{"kind":"song","trackId":899,"trackName":"One More Time","artistName":"Outro artista"}]}
                        """));
        assertThatThrownBy(() -> service.getMusicDetails("42")).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThatThrownBy(() -> service.getMusicDetails("42")).isInstanceOf(ExternalServiceUnavailableException.class);
        verify(restTemplate, never()).getForObject(URI.create(
                "https://itunes.apple.com/lookup?id=899&entity=song&country=BR&limit=200"), JsonNode.class);
    }

    @Test
    void respostaNulaOuMalformadaAcionaReserva() throws Exception {
        respond("https://api.deezer.com/search/track?q=malformado&limit=20", "{\"data\":{}}");
        respond("https://itunes.apple.com/search?term=malformado&media=music&entity=song&country=BR&limit=20",
                "{\"results\":[]}");
        assertThat(service.searchMusic("malformado")).isEmpty();
        verify(restTemplate).getForObject(URI.create(
                "https://itunes.apple.com/search?term=malformado&media=music&entity=song&country=BR&limit=20"), JsonNode.class);
        assertThatThrownBy(() -> service.getAlbumDetails("50"))
                .isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void consultasEmBrancoNaoChamamApis_eIdsInvalidosSaoRejeitados() {
        assertThat(service.searchMusic(" ")).isEmpty();
        assertThat(service.searchAlbums(null)).isEmpty();
        for (String invalidId : new String[]{"0", "-1", "abc", "itunes:abc", "itunes:", "../42", "9999999999999999999"}) {
            assertThatThrownBy(() -> service.getMusicDetails(invalidId))
                    .isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(restTemplate);
    }

    @Test
    void consultaComAcentosESimbolosTemParametrosCodificadosUmaVez() throws Exception {
        String url = "https://api.deezer.com/search/track?q=Jo%C3%A3o%20%26%20Maria%2B%25&limit=20";
        respond(url, "{\"data\":[]}");
        assertThat(service.searchMusic("João & Maria+%")).isEmpty();
        verifyNoMoreInteractionsAfterDeezerSearch(url);
    }

    private void verifyNoMoreInteractionsAfterDeezerSearch(String url) {
        verify(restTemplate).getForObject(URI.create(url), JsonNode.class);
        verifyNoMoreInteractions(restTemplate);
    }

    private void respond(String url, String json) throws Exception {
        when(restTemplate.getForObject(URI.create(url), JsonNode.class)).thenReturn(mapper.readTree(json));
    }

    private String track() {
        return """
                {"id":42,"title":"One More Time","duration":320,"release_date":"2000-11-13",
                 "artist":{"name":"Daft Punk"},"album":{"cover_big":"https://img.test/cover.jpg"}}
                """;
    }
}
