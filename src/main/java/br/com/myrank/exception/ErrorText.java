package br.com.myrank.exception;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mensagem de erro com partes que mudam ("O comentário passa de {0} caracteres."). O
 * modelo, com os {0}, é a chave da tradução em i18n/errors.json; as partes entram
 * depois de traduzir. Mensagem montada com "+" não dá para traduzir.
 */
public interface ErrorText {

    String template();

    List<String> args();

    /**
     * Troca {0}, {1}... pelas partes, numa passada só: um "{1}" digitado pela pessoa
     * dentro da parte {0} fica como está. Sem MessageFormat: ele estraga apóstrofos.
     */
    static String fill(String template, List<String> args) {
        if (args.isEmpty()) return template;
        Matcher m = SLOT.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            String part = index < args.size() ? args.get(index) : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(part));
        }
        m.appendTail(out);
        return out.toString();
    }

    Pattern SLOT = Pattern.compile("\\{(\\d+)}");

    static List<String> strings(Object... args) {
        return java.util.Arrays.stream(args).map(String::valueOf).toList();
    }
}
