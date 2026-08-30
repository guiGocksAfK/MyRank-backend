-- =========================================================
-- MyRank — Chat unificado (DM + grupo)
-- DIRECT = conversa de 2 membros (DM). GROUP = nome + N membros + 1 OWNER.
-- Não-lidas por membro via cursor last_read_message_id.
-- Real-time via STOMP/WebSocket (com polling de fallback).
-- =========================================================

CREATE TABLE conversations (
    id           BIGSERIAL           PRIMARY KEY,
    type         conversation_type   NOT NULL,
    name         VARCHAR(80),                             -- null em DIRECT
    description  VARCHAR(300),                             -- descrição do grupo (só GROUP); null = sem
    image_url    VARCHAR(1000),                           -- foto do grupo (URL)
    access       conversation_access NOT NULL DEFAULT 'CLOSED',  -- OPEN | REQUEST | CLOSED (só GROUP)
    invite_token VARCHAR(32),                             -- link de convite (só GROUP); null = sem link ativo
    created_by   BIGINT              NOT NULL,
    created_at   TIMESTAMP           NOT NULL DEFAULT now(),

    CONSTRAINT fk_conversations_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_conversations_invite ON conversations (invite_token) WHERE invite_token IS NOT NULL;

CREATE TABLE conversation_members (
    id                    BIGSERIAL                PRIMARY KEY,
    conversation_id       BIGINT                   NOT NULL,
    user_id               BIGINT                   NOT NULL,
    role                  conversation_member_role NOT NULL DEFAULT 'MEMBER',
    last_read_message_id  BIGINT,                            -- cursor de leitura
    joined_at             TIMESTAMP                NOT NULL DEFAULT now(),

    CONSTRAINT fk_cm_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_cm UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_cm_user ON conversation_members (user_id);
CREATE INDEX idx_cm_conversation ON conversation_members (conversation_id);

-- Pedidos de entrada em grupos REQUEST (presença = pendente).
CREATE TABLE conversation_join_requests (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT    NOT NULL,
    user_id          BIGINT    NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_cjr_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_cjr_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_cjr UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_cjr_conversation ON conversation_join_requests (conversation_id);

CREATE TABLE messages (
    id              BIGSERIAL     PRIMARY KEY,
    conversation_id BIGINT        NOT NULL,
    sender_id       BIGINT        NOT NULL,
    kind            message_kind  NOT NULL DEFAULT 'USER',   -- SYSTEM = "fulano criou o grupo" etc
    body            VARCHAR(2000) NOT NULL,
    reply_to_id     BIGINT,                                  -- resposta a outra mensagem
    edited_at       TIMESTAMP,                               -- not null = editada
    deleted_at      TIMESTAMP,                               -- not null = apagada (vira lápide)
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_reply FOREIGN KEY (reply_to_id)
        REFERENCES messages (id) ON DELETE SET NULL
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, id DESC);

-- Reações às mensagens: 1 por usuário por mensagem (troca o emoji ou remove).
CREATE TABLE message_reactions (
    id          BIGSERIAL   PRIMARY KEY,
    message_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    emoji       VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT fk_mr_message FOREIGN KEY (message_id)
        REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_mr_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_mr UNIQUE (message_id, user_id)
);

CREATE INDEX idx_mr_message ON message_reactions (message_id);
