-- =========================================================
-- MyRank — Social: follow, takes, feed materializado e reações do feed
-- =========================================================

-- FOLLOW (relação social entre usuários)
CREATE TABLE follow (
    id            BIGSERIAL PRIMARY KEY,
    follower_id   BIGINT    NOT NULL,
    followed_id   BIGINT    NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follow_followed FOREIGN KEY (followed_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_follow UNIQUE (follower_id, followed_id),
    CONSTRAINT chk_follow_no_self CHECK (follower_id <> followed_id)
);

CREATE INDEX idx_follow_follower ON follow (follower_id);
CREATE INDEX idx_follow_followed ON follow (followed_id);

-- PEDIDO DE SEGUIR (só existe enquanto o alvo é privado e não aprovou/recusou).
-- Aprovar → vira linha em `follow` e o pedido é apagado. Recusar/cancelar → apaga.
CREATE TABLE follow_request (
    id            BIGSERIAL PRIMARY KEY,
    requester_id  BIGINT    NOT NULL,
    target_id     BIGINT    NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_freq_requester FOREIGN KEY (requester_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_freq_target FOREIGN KEY (target_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_follow_request UNIQUE (requester_id, target_id),
    CONSTRAINT chk_freq_no_self CHECK (requester_id <> target_id)
);

CREATE INDEX idx_freq_target ON follow_request (target_id);

-- Opinião curta (<=280) presa a uma obra.
CREATE TABLE takes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    work_id     BIGINT       NOT NULL,
    text        VARCHAR(280) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_takes_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_takes_work FOREIGN KEY (work_id)
        REFERENCES works (id) ON DELETE CASCADE
);

CREATE INDEX idx_takes_work ON takes (work_id);

-- Comentários num take. 2 níveis (estilo Instagram): parent_comment_id null = raiz,
-- senão = resposta. Responder a uma resposta continua preso à raiz (o serviço achata).
CREATE TABLE take_comment (
    id                BIGSERIAL    PRIMARY KEY,
    take_id           BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    parent_comment_id BIGINT,
    text              VARCHAR(500) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    edited_at         TIMESTAMP,                               -- not null = editado

    CONSTRAINT fk_tc_take   FOREIGN KEY (take_id)           REFERENCES takes (id)        ON DELETE CASCADE,
    CONSTRAINT fk_tc_user   FOREIGN KEY (user_id)           REFERENCES users (id)        ON DELETE CASCADE,
    CONSTRAINT fk_tc_parent FOREIGN KEY (parent_comment_id) REFERENCES take_comment (id) ON DELETE CASCADE
);

CREATE INDEX idx_take_comment_take   ON take_comment (take_id, created_at);
CREATE INDEX idx_take_comment_parent ON take_comment (parent_comment_id);

-- Feed materializado: 1 linha por ação relevante (RATED, ADDED, BADGE, TAKE).
CREATE TABLE feed_events (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL,          -- ator
    type        feed_event_type NOT NULL,
    work_id     BIGINT,                            -- RATED / ADDED / TAKE
    badge_id    BIGINT,                            -- BADGE
    take_id     BIGINT,                            -- TAKE
    score       DECIMAL(4,2),                      -- snapshot da nota no momento
    created_at  TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_feed_events_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_feed_events_work  FOREIGN KEY (work_id)  REFERENCES works (id)  ON DELETE CASCADE,
    CONSTRAINT fk_feed_events_badge FOREIGN KEY (badge_id) REFERENCES badges (id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_events_take  FOREIGN KEY (take_id)  REFERENCES takes (id)  ON DELETE CASCADE
);

CREATE INDEX idx_feed_events_user_created ON feed_events (user_id, created_at DESC);

-- Reação a um item do feed. Uma por usuário por evento (troca de tipo = update).
CREATE TABLE feed_reactions (
    id             BIGSERIAL     PRIMARY KEY,
    feed_event_id  BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    kind           reaction_kind NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT fk_feed_reactions_event FOREIGN KEY (feed_event_id)
        REFERENCES feed_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_reactions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_feed_reactions UNIQUE (feed_event_id, user_id)
);

CREATE INDEX idx_feed_reactions_event ON feed_reactions (feed_event_id);
