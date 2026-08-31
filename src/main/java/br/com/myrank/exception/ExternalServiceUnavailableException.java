package br.com.myrank.exception;

/**
 * Lançada quando uma API externa (TMDB, RAWG, MyAnimeList, etc.) está indisponível
 * ou retorna erro de servidor (5xx). Sinaliza ao controller que o problema é
 * externo, não uma falha de autenticação ou dado inválido do usuário.
 */
public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}