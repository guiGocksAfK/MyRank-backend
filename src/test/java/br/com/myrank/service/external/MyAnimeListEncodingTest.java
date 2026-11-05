package br.com.myrank.service.external;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Busca com mais de uma palavra ("Cowboy Bebop") com um RestTemplate de verdade: o
 * endereço já sai codificado do UriComponentsBuilder e não pode ser codificado de
 * novo (virava "Cowboy%2520Bebop" e o MAL não achava nada).
 */
class MyAnimeListEncodingTest {

    @Test
    void buscaComEspaco_codificaUmaVezSo() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.myanimelist.net/v2/anime?q=Cowboy%20Bebop&limit=20&fields=id,title,main_picture,start_date"))
                .andRespond(withSuccess("""
                        {"data":[{"node":{"id":1,"title":"Cowboy Bebop","start_date":"1998-04-03",
                         "main_picture":{"large":"https://img.test/cb.jpg"}}}]}
                        """, MediaType.APPLICATION_JSON));

        var results = new MyAnimeListService(restTemplate, "id").searchAnime("Cowboy Bebop");

        server.verify();
        assertThat(results).hasSize(1);
    }
}
