package br.com.myrank.exception;

/**
 * Senha certa, mas a conta ainda não confirmou o email → 403 com
 * {@code code=EMAIL_NOT_VERIFIED}, pra tela de login oferecer o reenvio do link.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Confirme seu email antes de entrar. Enviamos um link para a sua caixa de entrada.");
    }
}
