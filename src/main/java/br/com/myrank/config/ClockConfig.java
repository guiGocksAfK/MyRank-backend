package br.com.myrank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /**
     * Relógio único do app. Quem depende de prazo (validade de código, por
     * exemplo) recebe este bean em vez de chamar Instant.now(), e o teste passa
     * um relógio próprio pra simular o tempo passando.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
