package br.com.myrank.config;

import org.apache.catalina.Context;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Por padrão o Tomcat só faz o parse de {@code multipart/form-data} em requisições POST.
 * O upload/troca de avatar usa PUT (/api/users/me/avatar), então sem isso o corpo
 * multipart é ignorado e o {@code @RequestParam("file")} chega vazio (400).
 */
@Configuration
public class MultipartConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> allowMultipartOnPut() {
        return factory -> factory.addContextCustomizers(
                (Context context) -> context.setAllowCasualMultipartParsing(true));
    }
}
