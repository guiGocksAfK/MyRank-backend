package br.com.myrank.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint leve e público para checar se a aplicação já subiu.
 * Usado pelo front para detectar o cold start do plano gratuito do Render
 * e ficar re-tentando até o servidor responder.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
