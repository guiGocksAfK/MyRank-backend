package br.com.myrank.exception;

import java.util.List;

/**
 * Erro de regra (400) com partes que mudam. {@code getMessage()} já vem em português,
 * então log e testes continuam lendo a frase pronta; quem traduz usa o modelo.
 */
public class TemplatedArgumentException extends IllegalArgumentException implements ErrorText {

    private final String template;
    private final List<String> args;

    private TemplatedArgumentException(String template, List<String> args) {
        super(ErrorText.fill(template, args));
        this.template = template;
        this.args = args;
    }

    public static TemplatedArgumentException of(String template, Object... args) {
        return new TemplatedArgumentException(template, ErrorText.strings(args));
    }

    @Override
    public String template() { return template; }

    @Override
    public List<String> args() { return args; }
}
