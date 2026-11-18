package br.com.myrank.config;

import br.com.myrank.exception.ErrorText;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mensagens de erro para a tela, no idioma da pessoa (PT, EN, ES).
 *
 * <p>O texto em português fica no código, onde o erro nasce; ele é a chave das
 * traduções em {@code i18n/errors.json}. O site manda o idioma no
 * {@code Accept-Language}; sem ele (o bot de Discord, por exemplo), vale português.
 *
 * <p>Só aparece na tela o que está no arquivo: erro técnico de biblioteca ou de
 * configuração do servidor nunca chega cru — vira uma das mensagens genéricas daqui,
 * e o detalhe fica no log. Um teste confere que toda mensagem escrita no código tem
 * tradução.
 *
 * <p>Uma instância só, carregada na primeira vez ({@link #get()}): filtros e handlers
 * são montados à mão em testes, então o catálogo não depende do Spring.
 */
public final class ErrorMessages {

    /** Erro nosso que ninguém esperava (500). */
    public static final String UNEXPECTED = "Algo deu errado do nosso lado. Tente de novo em instantes.";
    /** Pedido que o site não deveria ter montado assim (site desatualizado, por exemplo). */
    public static final String BAD_REQUEST = "Não foi possível concluir o pedido. Atualize a página e tente de novo.";
    /** Rota que não existe: quase sempre site e servidor em versões diferentes. */
    public static final String NOT_FOUND = "Essa função não está disponível agora. Atualize a página e tente de novo.";
    /** Algo do servidor ou de fora dele está fora do ar. */
    public static final String UNAVAILABLE = "Não foi possível concluir agora. Tente de novo em instantes.";
    /** Validação sem texto próprio. */
    public static final String INVALID_DATA = "Algum dado veio inválido. Confira e tente de novo.";

    private static final List<String> LANGUAGES = List.of("en", "es");

    private static volatile ErrorMessages instance;

    private final Map<String, Map<String, String>> catalog;

    ErrorMessages(Map<String, Map<String, String>> catalog) {
        this.catalog = catalog;
    }

    public static ErrorMessages get() {
        ErrorMessages current = instance;
        if (current == null) {
            synchronized (ErrorMessages.class) {
                current = instance;
                if (current == null) {
                    current = new ErrorMessages(load());
                    instance = current;
                }
            }
        }
        return current;
    }

    /** Todas as mensagens conhecidas, em português (para o teste do catálogo). */
    public java.util.Set<String> templates() {
        return catalog.keySet();
    }

    static Map<String, Map<String, String>> load() {
        try (InputStream in = new ClassPathResource("i18n/errors.json").getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("i18n/errors.json não pôde ser lido", e);
        }
    }

    /** A mensagem (ou o modelo, com {0}) tem tradução e pode ir para a tela. */
    public boolean knows(String template) {
        return template != null && catalog.containsKey(template);
    }

    /** Traduz um modelo conhecido e preenche as partes. Desconhecido volta como veio. */
    public String translate(String template, List<String> args, String language) {
        String text = template;
        Map<String, String> translations = catalog.get(template);
        if (translations != null && LANGUAGES.contains(language)) {
            text = translations.getOrDefault(language, template);
        }
        return ErrorText.fill(text, args);
    }

    public String translate(String template, String language) {
        return translate(template, List.of(), language);
    }

    /**
     * O texto que pode ir para a tela, ou {@code null} se o erro não é um dos nossos
     * (aí quem chamou usa uma das genéricas e registra o detalhe no log).
     */
    public String forUser(Throwable ex, String language) {
        if (ex instanceof ErrorText text && knows(text.template())) {
            return translate(text.template(), text.args(), language);
        }
        String message = ex.getMessage();
        return knows(message) ? translate(message, language) : null;
    }

    /** "en", "es" ou "pt", pelo primeiro idioma do Accept-Language. */
    public static String language(HttpServletRequest request) {
        String header = request == null ? null : request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) return "pt";
        String first = header.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (first.startsWith("en")) return "en";
        if (first.startsWith("es")) return "es";
        return "pt";
    }
}
