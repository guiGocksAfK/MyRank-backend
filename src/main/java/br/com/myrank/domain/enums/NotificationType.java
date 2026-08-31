package br.com.myrank.domain.enums;

/** Espelha o enum PG `notification_type`. */
public enum NotificationType {
    REACTION, FOLLOW, TAKE,
    /** Um moderador te adicionou num grupo. */
    GROUP_ADDED,
    /** Seu pedido pra entrar num grupo foi aprovado. */
    GROUP_APPROVED,
    /** Alguém pediu pra te seguir (seu perfil é privado). */
    FOLLOW_REQUEST,
    /** O dono de um perfil privado aceitou seu pedido pra seguir. */
    FOLLOW_ACCEPTED
}
