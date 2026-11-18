package br.com.myrank.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda mensagem de erro escrita no código precisa de tradução em i18n/errors.json.
 * Sem ela, a tela mostraria a genérica no lugar da mensagem certa. Este teste lê o
 * código-fonte, então pega mensagem nova no mesmo commit em que ela nasce.
 */
class ErrorMessagesCatalogTest {

    private static final String STR = "\"((?:[^\"\\\\]|\\\\.)*)\"";
    private static final List<Pattern> PATTERNS = Stream.of(
            "new (?:IllegalArgumentException|IllegalStateException|BadCredentialsException|ExternalServiceUnavailableException)\\(\\s*" + STR,
            "TemplatedArgumentException\\.of\\(\\s*" + STR,
            "\\b(?:LIMIT_MESSAGE|TOO_MANY|UNEXPECTED|BAD_REQUEST|NOT_FOUND|UNAVAILABLE|INVALID_DATA)\\s*=\\s*" + STR,
            "message\\s*=\\s*" + STR,
            "new ResponseStatusException\\(\\s*HttpStatus\\.\\w+,\\s*" + STR,
            "\\?\\s*" + STR + "\\s*:\\s*" + STR,
            "emailFrom\\([^,]+,\\s*" + STR,
            "super\\(\\s*" + STR,
            "\\btext\\(\\s*" + STR
    ).map(p -> Pattern.compile(p, Pattern.DOTALL)).toList();

    /** Configuração do servidor e falhas internas: vão para o log, a tela mostra a genérica. */
    private static final Set<String> INTERNAL = Set.of(
            "Discord OAuth não configurado (client id/secret).", "Discord OAuth não configurado.",
            "Google OAuth não configurado (client id/secret).", "Google OAuth não configurado.",
            "Falha ao calcular o hash da seleção.", "Falha ao ler a análise salva.",
            "Falha ao salvar o chat da análise.", "Falha ao serializar a análise.",
            "Personalizado não tem API.", "SHA-256 indisponível.",
            "A análise por IA não está configurada no servidor. Defina GEMINI_API_KEY e tente de novo.",
            "O chat da IA não está configurado no servidor. Defina GEMINI_API_KEY e tente de novo.",
            "A integração com o MyAnimeList não está configurada.",
            "JWT_SECRET não configurado ou com menos de ",
            "i18n/errors.json não pôde ser lido"
    );

    /** Respostas do bot de Discord: só em português, o bot não manda idioma. */
    private static final String BOT_FILTER = "BotAuthenticationFilter.java";

    private static Set<String> messagesInSource() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (file.endsWith(BOT_FILTER)) continue;
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (Pattern pattern : PATTERNS) {
                    Matcher m = pattern.matcher(source);
                    while (m.find()) {
                        for (int g = 1; g <= m.groupCount(); g++) {
                            String text = m.group(g).replace("\\\"", "\"");
                            // pedaço de código ("manga"), não frase
                            boolean sentence = text.contains(" ") && text.matches("(?s).*[.?!]$");
                            if (sentence && !INTERNAL.contains(text)) found.add(text);
                        }
                    }
                }
            }
        }
        return found;
    }

    @Test
    void todaMensagemDoCodigo_temTraducao() throws IOException {
        Set<String> source = messagesInSource();
        assertThat(source).as("mensagens encontradas no código").hasSizeGreaterThan(150);
        assertThat(ErrorMessages.get().templates())
                .as("mensagens sem tradução em i18n/errors.json")
                .containsAll(source);
    }

    @Test
    void catalogo_naoTemSobra_eTodaTraducaoTemAsMesmasPartes() throws IOException {
        Set<String> source = messagesInSource();
        Map<String, Map<String, String>> catalog = ErrorMessages.load();
        assertThat(source).as("traduções de mensagens que não existem mais").containsAll(catalog.keySet());
        Pattern slot = Pattern.compile("\\{\\d}");
        catalog.forEach((pt, translations) -> {
            List<String> slots = slot.matcher(pt).results().map(r -> r.group()).sorted().toList();
            for (String language : List.of("en", "es")) {
                String text = translations.get(language);
                assertThat(text).as(language + " de: " + pt).isNotBlank();
                assertThat(slot.matcher(text).results().map(r -> r.group()).sorted().toList())
                        .as("partes {0} em " + language + " de: " + pt).isEqualTo(slots);
            }
        });
    }
}
