package br.com.myrank.exception;

import br.com.myrank.dto.ErrorResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Trata falhas vindas das integrações externas (TMDB, RAWG, Jikan) de forma
 * centralizada. Sem isso, uma RestClientException não capturada nos services
 * sobe sem tratamento específico e pode ser confundida com erro de autenticação
 * pelo cliente — por isso sempre devolvemos um status e mensagem claros aqui.
 *
 * Escopo: só os controllers de integração externa (ExternalSearchController).
 * Não interfere em exceções de outras partes da aplicação (Work, Category, etc.).
 */
@RestControllerAdvice(basePackages = "br.com.myrank.controller")
public class ExternalApiExceptionHandler {

    /** Erro já tratado/relançado explicitamente pelos nossos services externos. */
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> handleExternalServiceUnavailable(ExternalServiceUnavailableException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** A API externa respondeu, mas com erro 5xx (ex.: 502/503/504 do próprio provedor). */
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponseDTO> handleExternalServerError(HttpServerErrorException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "O serviço externo está indisponível no momento. Tente novamente em instantes."
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** A API externa respondeu com erro 4xx (ex.: chave inválida, recurso não encontrado). */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponseDTO> handleExternalClientError(HttpClientErrorException ex) {
        HttpStatus status = ex.getStatusCode() == HttpStatus.NOT_FOUND
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_GATEWAY;
        String message = ex.getStatusCode() == HttpStatus.NOT_FOUND
                ? "Obra não encontrada na base externa."
                : "Erro ao se comunicar com o serviço externo.";
        ErrorResponseDTO body = new ErrorResponseDTO(status.value(), message);
        return ResponseEntity.status(status).body(body);
    }

    /** Timeout, DNS, conexão recusada — a chamada nem chegou a ter resposta HTTP. */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceAccess(ResourceAccessException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Não foi possível conectar ao serviço externo. Verifique sua conexão ou tente novamente em instantes."
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** Fallback: qualquer outra falha de cliente HTTP (RestTemplate) não coberta acima. */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericRestClientError(RestClientException ex) {
        ErrorResponseDTO body = new ErrorResponseDTO(
                HttpStatus.BAD_GATEWAY.value(),
                "Erro inesperado ao se comunicar com o serviço externo."
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}