package br.com.myrank.config;

import br.com.myrank.exception.TemplatedArgumentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/** O que chega na tela: texto nosso, no idioma da pessoa, nunca o detalhe técnico. */
class GlobalExceptionHandlerLanguageTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MockHttpServletRequest request(String language) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/works");
        if (language != null) request.addHeader("Accept-Language", language);
        return request;
    }

    @Test
    void mensagemNossa_noIdiomaDaPessoa_eEmPortuguesSemCabecalho() {
        var ex = new IllegalArgumentException("Tabela não encontrada.");
        assertThat(handler.handleIllegalArgument(ex, request("en")).getBody())
                .containsEntry("message", "Table not found.");
        assertThat(handler.handleIllegalArgument(ex, request("es-ES,es;q=0.9")).getBody())
                .containsEntry("message", "Tabla no encontrada.");
        // o bot de Discord não manda idioma: português
        assertThat(handler.handleIllegalArgument(ex, request(null)).getBody())
                .containsEntry("message", "Tabela não encontrada.");
    }

    @Test
    void mensagemComPartes_traduzOModelo_eMantemAsPartes() {
        var ex = TemplatedArgumentException.of("O comentário passa de {0} caracteres.", 500);
        assertThat(ex.getMessage()).isEqualTo("O comentário passa de 500 caracteres.");
        assertThat(handler.handleIllegalArgument(ex, request("en")).getBody())
                .containsEntry("message", "The comment is over 500 characters.");
    }

    @Test
    void idioma_respeitaAOrdemDePreferencia() {
        assertThat(ErrorMessages.language(request("fr-FR, en-US;q=0.9, es;q=0.8"))).isEqualTo("en");
        assertThat(ErrorMessages.language(request("pt;q=0.5, en;q=0.9"))).isEqualTo("en");
        assertThat(ErrorMessages.language(request("de, es;q=0.7"))).isEqualTo("es");
        assertThat(ErrorMessages.language(request("fr, de"))).isEqualTo("pt");
        assertThat(ErrorMessages.language(request(";;;q=abc"))).isEqualTo("pt");
    }

    @Test
    void partes_entramNumaPassadaSo() {
        // o nome digitado pela pessoa tem "{1}": não pode virar a segunda parte
        assertThat(br.com.myrank.exception.ErrorText.fill("{0} e {1}", java.util.List.of("a{1}", "b")))
                .isEqualTo("a{1} e b");
        assertThat(br.com.myrank.exception.ErrorText.fill("custa $5 {0}", java.util.List.of("\\$1")))
                .isEqualTo("custa $5 \\$1");
    }

    @Test
    void emailNaoVerificado_traduzido() {
        var ex = new br.com.myrank.exception.EmailNotVerifiedException();
        assertThat(handler.handleEmailNotVerified(ex, request("en")).getBody())
                .containsEntry("message", "Confirm your email before signing in. We sent a link to your inbox.")
                .containsEntry("code", "EMAIL_NOT_VERIFIED");
    }

    @Test
    void erroDeBiblioteca_naoVaiParaATela() {
        var ex = new NumberFormatException("For input string: \"abc\"");
        var response = handler.handleIllegalArgument(ex, request("pt-BR"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", ErrorMessages.BAD_REQUEST);
    }

    @Test
    void erroSemTexto_naoQuebraOTratamento() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException(), request(null));
        assertThat(response.getBody()).containsEntry("message", ErrorMessages.BAD_REQUEST);
    }

    @Test
    void configuracaoDoServidor_ficaNoLog_eATelaVeAGenerica() {
        var ex = new IllegalStateException("Discord OAuth não configurado (client id/secret).");
        var response = handler.handleIllegalState(ex, request("en"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("message", "Couldn't finish right now. Try again in a moment.");
    }

    @Test
    void rotaInexistente_explicaSemJargao() {
        var ex = new NoResourceFoundException(HttpMethod.POST, "/api/users/me/discord");
        assertThat(handler.handleNoResourceFound(ex, request(null)).getBody())
                .containsEntry("message", ErrorMessages.NOT_FOUND);
    }
}
