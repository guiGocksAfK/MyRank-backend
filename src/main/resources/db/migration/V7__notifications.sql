-- =========================================================
-- MyRank — Notificações (sininho)
-- Reações são AGREGADAS: 1 linha por (destinatário, feed_event, reaction_kind),
-- com actor_count e o último ator. Follow e take são 1 linha por evento.
-- GROUP_ADDED / GROUP_APPROVED: 1 linha por (destinatário, conversa); re-adição
-- reaproveita a linha (bump de updated_at + read=false).
-- Depende de feed_events (V5) e conversations (V6).
-- =========================================================

CREATE TABLE notifications (
    id              BIGSERIAL         PRIMARY KEY,
    user_id         BIGINT            NOT NULL,       -- destinatário
    type            notification_type NOT NULL,
    actor_id        BIGINT,                           -- último/único ator
    actor_count     INT               NOT NULL DEFAULT 1,
    feed_event_id   BIGINT,
    conversation_id BIGINT,                           -- contexto de GROUP_ADDED / GROUP_APPROVED
    reaction_kind   reaction_kind,
    read            BOOLEAN           NOT NULL DEFAULT false,
    created_at      TIMESTAMP         NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP         NOT NULL DEFAULT now(),

    CONSTRAINT fk_notif_user  FOREIGN KEY (user_id)         REFERENCES users (id)         ON DELETE CASCADE,
    CONSTRAINT fk_notif_actor FOREIGN KEY (actor_id)        REFERENCES users (id)         ON DELETE CASCADE,
    CONSTRAINT fk_notif_event FOREIGN KEY (feed_event_id)   REFERENCES feed_events (id)   ON DELETE CASCADE,
    CONSTRAINT fk_notif_conv  FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user ON notifications (user_id, updated_at DESC);
CREATE UNIQUE INDEX uq_notif_reaction ON notifications (user_id, feed_event_id, reaction_kind) WHERE type = 'REACTION';
CREATE UNIQUE INDEX uq_notif_follow   ON notifications (user_id, actor_id) WHERE type = 'FOLLOW';
CREATE UNIQUE INDEX uq_notif_follow_req ON notifications (user_id, actor_id) WHERE type = 'FOLLOW_REQUEST';
CREATE UNIQUE INDEX uq_notif_follow_acc ON notifications (user_id, actor_id) WHERE type = 'FOLLOW_ACCEPTED';
CREATE UNIQUE INDEX uq_notif_take     ON notifications (user_id, feed_event_id) WHERE type = 'TAKE';
CREATE UNIQUE INDEX uq_notif_group    ON notifications (user_id, conversation_id, type)
    WHERE type IN ('GROUP_ADDED', 'GROUP_APPROVED');
