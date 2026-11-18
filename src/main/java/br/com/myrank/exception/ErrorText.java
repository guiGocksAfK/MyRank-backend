package br.com.myrank.exception;

import java.util.List;

/**
 * Mensagem de erro com partes que mudam ("O comentário passa de {0} caracteres."). O
 * modelo, com os {0}, é a chave da tradução em i18n/errors.json; as partes entram
 * depois de traduzir. Mensagem montada com "+" não dá para traduzir.
 */
public interface ErrorText {

    String template();

    List<String> args();

    /** Troca {0}, {1}... pelas partes. Sem MessageFormat: ele estraga apóstrofos. */
    static String fill(String template, List<String> args) {
        String text = template;
        for (int i = 0; i < args.size(); i++) {
            text = text.replace("{" + i + "}", args.get(i));
        }
        return text;
    }

    static List<String> strings(Object... args) {
        return java.util.Arrays.stream(args).map(String::valueOf).toList();
    }
}
