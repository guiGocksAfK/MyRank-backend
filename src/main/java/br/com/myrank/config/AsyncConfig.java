package br.com.myrank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor limitado para trabalho de fundo (hoje só o recálculo de badges,
 * que roda a cada CRUD de obra). Pool pequeno + fila limitada + CallerRuns:
 * se encher, a request que disparou paga o custo — nunca cresce sem limite.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "badgeExecutor")
    public Executor badgeExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("badge-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
