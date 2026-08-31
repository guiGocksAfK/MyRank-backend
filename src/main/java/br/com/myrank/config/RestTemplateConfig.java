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
     * User-Agent identificando o app. Inofensivo para todas as integrações
     * (TMDB, RAWG, MyAnimeList, Google Books) e evita bloqueios de WAF que
     * rejeitam o User-Agent padrão do Java (ex.: "Java/17.0.12").
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