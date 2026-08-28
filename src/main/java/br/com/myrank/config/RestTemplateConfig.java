package br.com.myrank.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * User-Agent identificando o app. Necessário para a Jikan: o Cloudflare dela
     * responde 504 ("MyAnimeList may be down") para o User-Agent padrão do Java
     * (ex.: "Java/17.0.12"). Inofensivo para TMDB/RAWG/Google Books.
     */
    private static final String USER_AGENT = "MyRank/1.0 (+https://github.com/guiGocksAfK/MyRank)";

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .additionalInterceptors((request, body, execution) -> {
                    request.getHeaders().set(HttpHeaders.USER_AGENT, USER_AGENT);
                    return execution.execute(request, body);
                })
                .build();
    }
}