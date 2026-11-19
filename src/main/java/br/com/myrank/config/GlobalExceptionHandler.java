package br.com.myrank.config;

import br.com.myrank.exception.EmailNotVerifiedException;
import br.com.myrank.exception.ExternalServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Toda resposta de erro sai como {@code {"message": "..."}}, num texto feito para a
 * pessoa e no idioma dela (ver {@link ErrorMessages}). Detalhe técnico nunca vai para
 * a tela: fica no log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorMessages messages = ErrorMessages.get();

    private ResponseEntity<Map<String, String>> reply(HttpStatus status, String text) {
        return ResponseEntity.status(status).body(Map.of("message", text));
    }

    private ResponseEntity<Map<String, String>> generic(HttpStatus status, String template, HttpServletRequest request) {
        return reply(status, messages.translate(template, ErrorMessages.language(request)));
    }

    /** Erros de @Valid nos @RequestBody → 400 com a mensagem de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        String language = ErrorMessages.language(request);
        Set<String> texts = new LinkedHashSet<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            String own = fe.getDefaultMessage();
            // validação sem texto nosso vem com a frase padrão do Java, em inglês
            texts.add(messages.translate(messages.knows(own) ? own : ErrorMessages.INVALID_DATA, language));
        });
        if (texts.isEmpty()) texts.add(messages.translate(ErrorMessages.INVALID_DATA, language));
        return reply(HttpStatus.BAD_REQUEST, String.join(" ", texts));
    }

    /** Corpo JSON malformado / ilegível → 400, não 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                    HttpServletRequest request) {
        // um enum desconhecido no corpo chega aqui com a mensagem nossa embrulhada
        Throwable cause = ex.getMostSpecificCause();
        String own = cause instanceof IllegalArgumentException ? messages.forUser(cause, ErrorMessages.language(request)) : null;
        if (own != null) return reply(HttpStatus.BAD_REQUEST, own);
        log.warn("Corpo ilegível em {} {}: {}", request.getMethod(), request.getRequestURI(), cause.getMessage());
        return generic(HttpStatus.BAD_REQUEST, ErrorMessages.BAD_REQUEST, request);
    }

    /** Rede de segurança: qualquer exceção não mapeada → 500 genérico, sem vazar detalhe interno. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        return generic(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.UNEXPECTED, request);
    }

    /** Erro de regra (400). Só a mensagem nossa vai para a tela; a de biblioteca vira genérica. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex,
                                                                     HttpServletRequest request) {
        String text = messages.forUser(ex, ErrorMessages.language(request));
        if (text != null) return reply(HttpStatus.BAD_REQUEST, text);
        log.warn("400 sem mensagem para a tela em {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return generic(HttpStatus.BAD_REQUEST, ErrorMessages.BAD_REQUEST, request);
    }

    /** Algo fora do ar (503). Mensagem de configuração do servidor fica só no log. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String text = messages.forUser(ex, ErrorMessages.language(request));
        if (text != null) return reply(HttpStatus.SERVICE_UNAVAILABLE, text);
        log.error("503 em {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return generic(HttpStatus.SERVICE_UNAVAILABLE, ErrorMessages.UNAVAILABLE, request);
    }

    /** API de fora (TMDB, RAWG...) fora do ar, fora da tela de busca (ex.: "Atualizar dados"). */
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleExternal(ExternalServiceUnavailableException ex,
                                                              HttpServletRequest request) {
        // sempre no log: é a única pista de qual serviço caiu e por quê
        log.warn("Serviço externo fora do ar em {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex.getCause());
        String text = messages.forUser(ex, ErrorMessages.language(request));
        if (text != null) return reply(HttpStatus.SERVICE_UNAVAILABLE, text);
        return generic(HttpStatus.SERVICE_UNAVAILABLE, ErrorMessages.UNAVAILABLE, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                      HttpServletRequest request) {
        log.warn("Parâmetro ausente em {}: {}", request.getRequestURI(), ex.getParameterName());
        return generic(HttpStatus.BAD_REQUEST, ErrorMessages.BAD_REQUEST, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                  HttpServletRequest request) {
        log.warn("Parâmetro inválido em {}: {}", request.getRequestURI(), ex.getName());
        return generic(HttpStatus.BAD_REQUEST, ErrorMessages.BAD_REQUEST, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex,
                                                                    HttpServletRequest request) {
        String reason = ex.getReason();
        String language = ErrorMessages.language(request);
        String text = messages.knows(reason) ? messages.translate(reason, language)
                : messages.translate(ErrorMessages.BAD_REQUEST, language);
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", text));
    }

    /** Rota inexistente → 404 sem log de erro (bots e scanners batem nisso o tempo todo). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFound(NoResourceFoundException ex,
                                                                     HttpServletRequest request) {
        return generic(HttpStatus.NOT_FOUND, ErrorMessages.NOT_FOUND, request);
    }

    /** Método HTTP não suportado na rota → 405 sem poluir log com erro 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                        HttpServletRequest request) {
        return generic(HttpStatus.METHOD_NOT_ALLOWED, ErrorMessages.NOT_FOUND, request);
    }

    /** Content-Type não suportado → 415 sem poluir log com erro 500. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                           HttpServletRequest request) {
        return generic(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorMessages.BAD_REQUEST, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex,
                                                                    HttpServletRequest request) {
        String text = messages.forUser(ex, ErrorMessages.language(request));
        return text != null ? reply(HttpStatus.UNAUTHORIZED, text)
                : generic(HttpStatus.UNAUTHORIZED, ErrorMessages.BAD_REQUEST, request);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleEmailNotVerified(EmailNotVerifiedException ex,
                                                                      HttpServletRequest request) {
        String language = ErrorMessages.language(request);
        String text = messages.forUser(ex, language);
        if (text == null) {
            log.warn("Email não verificado sem mensagem para a tela: {}", ex.getMessage());
            text = messages.translate(ErrorMessages.BAD_REQUEST, language);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", text, "code", "EMAIL_NOT_VERIFIED"));
    }
}
