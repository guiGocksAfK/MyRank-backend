package br.com.myrank.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** O que o card de Música e de Álbum precisa: artista na sugestão, álbum/faixas e capa nítida. */
class MusicCardDetailsTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final MusicSearchCache cache = new MusicSearchCache();
    private final ItunesService itunes = new ItunesService(restTemplate, cache);
    private final DeezerService deezer = new DeezerService(restTemplate, itunes, cache);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deezer_sugestaoTrazOArtista_eDetalhesTrazemAlbumOuFaixas() throws Exception {
        respond("https://api.deezer.com/search/track?q=Yesterday&limit=20", """
                {"data":[{"id":7,"title":"Yesterday","artist":{"name":"The Beatles"},"album":{"cover_big":"c"}}]}
                """);
        respond("https://api.deezer.com/track/7", """
                {"id":7,"title":"Yesterday","duration":125,"release_date":"1965-08-06",
                 "artist":{"name":"The Beatles"},"album":{"id":9,"title":"Help!","cover_big":"c"}}
                """);
        respond("https://api.deezer.com/album/9", """
                {"id":9,"title":"Help!","nb_tracks":14,"release_date":"1965-08-06","duration":2050,
                 "artist":{"name":"The Beatles"},"cover_big":"c"}
                """);

        assertThat(deezer.searchMusic("Yesterday").get(0).getSubtitle()).isEqualTo("The Beatles");
        assertThat(deezer.getMusicDetails("7").getDetails()).isEqualTo(Map.of("album", "Help!"));
        assertThat(deezer.getAlbumDetails("9").getDetails()).isEqualTo(Map.of("trackCount", 14));
    }

    @Test
    void itunes_capaVemEm600px_eDetalhesTrazemAlbumOuFaixas() throws Exception {
        respond("https://itunes.apple.com/search?term=Help&media=music&entity=album&country=BR&limit=20", """
                {"results":[{"wrapperType":"collection","collectionType":"Album","collectionId":95,
                 "collectionName":"Help!","artistName":"The Beatles",
                 "artworkUrl100":"https://is1.mzstatic.com/image/thumb/x/100x100bb.jpg"}]}
                """);
        respond("https://itunes.apple.com/lookup?id=95&entity=song&country=BR&limit=200", """
                {"results":[{"wrapperType":"collection","collectionType":"Album","collectionId":95,
                 "collectionName":"Help!","artistName":"The Beatles","trackCount":14}]}
                """);
        respond("https://itunes.apple.com/lookup?id=90&entity=song&country=BR&limit=200", """
                {"results":[{"kind":"song","trackId":90,"trackName":"Yesterday","collectionName":"Help!",
                 "artistName":"The Beatles","trackTimeMillis":125000}]}
                """);

        var suggestion = itunes.searchAlbums("Help").get(0);
        assertThat(suggestion.getPosterUrl()).isEqualTo("https://is1.mzstatic.com/image/thumb/x/600x600bb.jpg");
        assertThat(suggestion.getSubtitle()).isEqualTo("The Beatles");
        assertThat(itunes.getAlbumDetails("95").getDetails()).isEqualTo(Map.of("trackCount", 14));
        assertThat(itunes.getMusicDetails("90").getDetails()).isEqualTo(Map.of("album", "Help!"));
    }

    private void respond(String url, String json) throws Exception {
        when(restTemplate.getForObject(URI.create(url), JsonNode.class)).thenReturn(mapper.readTree(json));
    }
}
