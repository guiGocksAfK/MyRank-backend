package br.com.myrank.service.external;

import br.com.myrank.exception.ExternalServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItunesServiceTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ItunesService service = new ItunesService(restTemplate, new MusicSearchCache());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buscaMusicasUsaEntitySong_eFiltraResultadosDeOutrosTipos() throws Exception {
        String url = "https://itunes.apple.com/search?term=Discovery&media=music&entity=song&country=BR&limit=20";
        respond(url, """
                {"results":[{"kind":"music-video","trackId":800,"trackName":"Vídeo"},
                 {"kind":"song","trackId":900,"trackName":"One More Time","releaseDate":"2000-11-13T08:00:00Z",
                  "artworkUrl60":"https://img.test/60.jpg"},{"kind":"song","trackId":0,"trackName":"Inválido"}]}
                """);
        var results = service.searchMusic("Discovery");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("itunes:900");
        assertThat(results.get(0).getTitle()).isEqualTo("One More Time");
        assertThat(results.get(0).getPosterUrl()).isEqualTo("https://img.test/60.jpg");
        assertThat(results.get(0).getReleaseDate()).isEqualTo("2000-11-13");
        service.searchMusic("Discovery");
        verify(restTemplate, times(1)).getForObject(URI.create(url), JsonNode.class);
    }

    @Test
    void buscaAlbunsUsaEntityAlbum_eCollectionId() throws Exception {
        respond("https://itunes.apple.com/search?term=Discovery&media=music&entity=album&country=BR&limit=20", """
                {"results":[{"wrapperType":"collection","collectionType":"Album","collectionId":950,
                 "collectionName":"Discovery","releaseDate":"2001-03-12T08:00:00Z"},
                 {"wrapperType":"collection","collectionType":"TV Season","collectionId":951,"collectionName":"Série"}]}
                """);
        var results = service.searchAlbums("Discovery");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("itunes:950");
        assertThat(results.get(0).getReleaseDate()).isEqualTo("2001-03-12");
    }

    @Test
    void detalhesDaMusicaMapeiamMetadados_eMilissegundosEmMinutos() throws Exception {
        String url = "https://itunes.apple.com/lookup?id=900&entity=song&country=BR&limit=200";
        respond(url, """
                {"results":[{"kind":"song","trackId":901,"trackName":"Outra faixa"},
                 {"kind":"song","trackId":900,"trackName":"One More Time","artistName":"Daft Punk",
                  "trackTimeMillis":320000,"releaseDate":"2000-11-13T08:00:00Z",
                  "artworkUrl100":"https://img.test/100.jpg"}]}
                """);
        var details = service.getMusicDetails("900");
        assertThat(details.getTitle()).isEqualTo("One More Time");
        assertThat(details.getCreator()).isEqualTo("Daft Punk");
        assertThat(details.getImageUrl()).isEqualTo("https://img.test/100.jpg");
        assertThat(details.getReleaseDate()).isEqualTo("2000-11-13");
        assertThat(details.getTimeMinutes()).isEqualTo(5);
        service.getMusicDetails("900");
        verify(restTemplate, times(1)).getForObject(URI.create(url), JsonNode.class);
    }

    @Test
    void detalhesDoAlbumSomamApenasSuasFaixas_eArredondamNoFinal() throws Exception {
        respond("https://itunes.apple.com/lookup?id=950&entity=song&country=BR&limit=200", """
                {"results":[{"wrapperType":"collection","collectionType":"Album","collectionId":950,
                  "collectionName":"Discovery","artistName":"Daft Punk","releaseDate":"2001-03-12T08:00:00Z"},
                 {"kind":"song","trackId":900,"collectionId":950,"trackTimeMillis":89000},
                 {"kind":"song","trackId":901,"collectionId":950,"trackTimeMillis":89000},
                 {"kind":"song","trackId":902,"collectionId":999,"trackTimeMillis":999999},
                 {"kind":"music-video","collectionId":950,"trackTimeMillis":999999}]}
                """);
        var details = service.getAlbumDetails("950");
        assertThat(details.getTitle()).isEqualTo("Discovery");
        assertThat(details.getCreator()).isEqualTo("Daft Punk");
        assertThat(details.getReleaseDate()).isEqualTo("2001-03-12");
        assertThat(details.getTimeMinutes()).isEqualTo(3);
    }

    @Test
    void detalhesComMetadadosAusentesOuInvalidosUsamNulo_eDuracaoZero() throws Exception {
        respond("https://itunes.apple.com/lookup?id=900&entity=song&country=BR&limit=200", """
                {"results":[{"kind":"song","trackId":900,"trackName":"Faixa",
                 "releaseDate":"0000-00-00","trackTimeMillis":-1}]}
                """);
        var details = service.getMusicDetails("900");
        assertThat(details.getReleaseDate()).isNull();
        assertThat(details.getCreator()).isNull();
        assertThat(details.getImageUrl()).isNull();
        assertThat(details.getTimeMinutes()).isZero();
    }

    @Test
    void lookupVazioOuSemOIdPedidoProduzErroExternoClaro() throws Exception {
        String musicUrl = "https://itunes.apple.com/lookup?id=900&entity=song&country=BR&limit=200";
        when(restTemplate.getForObject(URI.create(musicUrl), JsonNode.class))
                .thenReturn(mapper.readTree("{\"results\":[]}"))
                .thenReturn(mapper.readTree("""
                        {"results":[{"kind":"song","trackId":900,"trackName":"Recuperado"}]}
                        """));
        respond("https://itunes.apple.com/lookup?id=950&entity=song&country=BR&limit=200", """
                {"results":[{"wrapperType":"collection","collectionType":"Album",
                 "collectionId":999,"collectionName":"Outro álbum"}]}
                """);
        assertThatThrownBy(() -> service.getMusicDetails("900")).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(service.getMusicDetails("900").getTitle()).isEqualTo("Recuperado");
        verify(restTemplate, times(2)).getForObject(URI.create(musicUrl), JsonNode.class);
        assertThatThrownBy(() -> service.getAlbumDetails("950")).isInstanceOf(ExternalServiceUnavailableException.class);
    }

    @Test
    void falhaDeConexaoNosDetalhesProduzErroExternoClaro() {
        when(restTemplate.getForObject(URI.create(
                "https://itunes.apple.com/lookup?id=900&entity=song&country=BR&limit=200"), JsonNode.class))
                .thenThrow(new ResourceAccessException("iTunes fora do ar"));
        assertThatThrownBy(() -> service.getMusicDetails("900"))
                .isInstanceOf(ExternalServiceUnavailableException.class).hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void consultasEmBrancoNaoChamamApis_eIdsInvalidosSaoRejeitados() {
        assertThat(service.searchMusic(null)).isEmpty();
        assertThat(service.searchAlbums(" ")).isEmpty();
        assertThatThrownBy(() -> service.getAlbumDetails("../950")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void consultaCodificaCaracteresEspeciaisSemCriarParametrosExtras() throws Exception {
        String url = "https://itunes.apple.com/search?term=Jo%C3%A3o%20%26%20Maria%2B%25&media=music&entity=song&country=BR&limit=20";
        respond(url, "{\"results\":[]}");
        assertThat(service.searchMusic("João & Maria+%")).isEmpty();
        verify(restTemplate).getForObject(URI.create(url), JsonNode.class);
    }

    private void respond(String url, String json) throws Exception {
        when(restTemplate.getForObject(URI.create(url), JsonNode.class)).thenReturn(mapper.readTree(json));
    }
}
