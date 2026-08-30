-- =========================================================
-- MyRank — Badges (catálogo) + user_badges (progresso por usuário)
-- =========================================================

-- Catálogo de badges. As linhas são sincronizadas no startup a partir do
-- enum BadgeDefinition (BadgeCatalogInitializer) — `code` é a chave estável
-- que liga cada linha à sua regra de cálculo em Java.
CREATE TABLE badges (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(60)  NOT NULL,
    bucket          VARCHAR(20)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    icon            VARCHAR(16),
    target_progress INT          NOT NULL DEFAULT 1,
    has_progress    BOOLEAN      NOT NULL DEFAULT true,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_badges_code UNIQUE (code)
);

CREATE TABLE user_badges (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT    NOT NULL,
    badge_id          BIGINT    NOT NULL,
    current_progress  INT       NOT NULL DEFAULT 0,
    unlocked_at       TIMESTAMP,

    CONSTRAINT fk_user_badges_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_badges_badge FOREIGN KEY (badge_id)
        REFERENCES badges (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_badges UNIQUE (user_id, badge_id)
);

CREATE INDEX idx_user_badges_user ON user_badges (user_id);
