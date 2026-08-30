-- =========================================================
-- MyRank — Usuários e estatísticas (1:1)
-- =========================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)         NOT NULL,
    email           VARCHAR(255),
    password_hash   VARCHAR(255),
    auth_provider   auth_provider_type  NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(255),
    avatar_url      VARCHAR(1000),                                 -- URL da foto (upload do usuário ou avatar do OAuth)
    bio             TEXT,
    plan            plan_type           NOT NULL DEFAULT 'FREE',
    is_public       BOOLEAN             NOT NULL DEFAULT true,
    language        VARCHAR(5)          NOT NULL DEFAULT 'PT',   -- idioma da interface (PT | EN | ES)
    last_seen_at    TIMESTAMP,                                     -- presença (bump no heartbeat do chat)
    created_at      TIMESTAMP           NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP           NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_language CHECK (language IN ('PT', 'EN', 'ES'))
);

-- Um provider_id só pode pertencer a um usuário por provedor OAuth.
CREATE UNIQUE INDEX uq_users_auth_provider_provider_id
    ON users (auth_provider, provider_id)
    WHERE provider_id IS NOT NULL;

-- USER_STATS (1:1 com users)
CREATE TABLE user_stats (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT UNIQUE NOT NULL,
    total_works_rated       INT           NOT NULL DEFAULT 0,
    total_hours_consumed    DECIMAL(10,2) NOT NULL DEFAULT 0,
    average_score           DECIMAL(4,2)  NOT NULL DEFAULT 0,
    updated_at              TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_stats_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);
