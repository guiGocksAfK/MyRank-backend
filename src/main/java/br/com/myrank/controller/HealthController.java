package br.com.myrank.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.myrank.repository.UserRepository;

/**
 * Endpoint leve e público para checar se a aplicação já subiu de verdade.
 * Usado pelo front para detectar o cold start do plano gratuito do Render e
 * ficar re-tentando até o servidor responder.
 *
 * Faz uma consulta real no banco (count, bem barata) em vez de só responder
 * 200 assim que o processo sobe — sem isso o front podia considerar
 * "acordado" antes do pool de conexões/JPA estarem prontos, e a primeira
 * ação de verdade do usuário (login, carregar perfil) ainda falhava.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final UserRepository userRepository;

    public HealthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        try {
            userRepository.count();
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "starting"));
        }
    }
}
