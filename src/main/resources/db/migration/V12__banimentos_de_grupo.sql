-- Quem é expulso de um grupo não volta sozinho (diretório, pedido ou link de convite).
-- Só um moderador adicionando de novo desfaz o banimento. Sair por conta própria não bane.
CREATE TABLE conversation_bans (
    conversation_id  BIGINT    NOT NULL,
    user_id          BIGINT    NOT NULL,
    banned_by        BIGINT,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT pk_conversation_bans PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_cb_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_cb_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cb_banned_by FOREIGN KEY (banned_by)
        REFERENCES users (id) ON DELETE SET NULL
);
