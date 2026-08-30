package br.com.myrank.domain.enums;

/** Espelha o enum PG `conversation_access`. Só relevante em GROUP. */
public enum ConversationAccess {
    /** Qualquer um entra na hora. */
    OPEN,
    /** Precisa pedir; um moderador aprova. */
    REQUEST,
    /** Ninguém pede/entra sozinho; só quem os admins adicionam. */
    CLOSED
}
