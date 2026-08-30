package br.com.myrank.domain.enums;

/**
 * Espelha o enum PG `conversation_member_role`.
 * Hierarquia (menor rank = mais poder): OWNER < ADMIN < MOD < MEMBER.
 */
public enum ConversationMemberRole {
    OWNER, ADMIN, MOD, MEMBER;

    public int rank() {
        return ordinal();
    }

    /** Aprovar pedidos, expulsar membros comuns, apagar qualquer mensagem. */
    public boolean canModerate() {
        return this == OWNER || this == ADMIN || this == MOD;
    }

    /** Editar nome/foto/acesso do grupo. */
    public boolean canEditGroup() {
        return this == OWNER || this == ADMIN;
    }

    /** Promover/rebaixar cargos (até ADMIN). */
    public boolean canManageRoles() {
        return this == OWNER || this == ADMIN;
    }
}
