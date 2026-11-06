package br.com.myrank.domain.enums;

/**
 * Grupos de aviso que a pessoa pode silenciar nas configurações. Cada tipo de
 * notificação pertence a um grupo; um grupo silenciado nem chega a ser criado.
 */
public enum NotificationGroup {
    /** Novos seguidores, pedidos pra seguir e pedidos aceitos. */
    FOLLOWS,
    /** Reações nas suas atividades. */
    REACTIONS,
    /** Comentários e respostas nos seus takes. */
    COMMENTS,
    /** Takes novos de quem você segue. */
    TAKES,
    /** Entrada em grupos do chat. */
    GROUPS;

    public static NotificationGroup of(NotificationType type) {
        return switch (type) {
            case FOLLOW, FOLLOW_REQUEST, FOLLOW_ACCEPTED -> FOLLOWS;
            case REACTION -> REACTIONS;
            case TAKE_COMMENT -> COMMENTS;
            case TAKE -> TAKES;
            case GROUP_ADDED, GROUP_APPROVED -> GROUPS;
        };
    }
}
