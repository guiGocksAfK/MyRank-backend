-- =========================================================
-- MyRank - Schema inicial
-- V1__init_schema.sql
-- =========================================================

-- ---------------------------------------------------------
-- ENUM TYPES
-- ---------------------------------------------------------
CREATE TYPE auth_provider_type AS ENUM ('LOCAL', 'GOOGLE', 'DISCORD');
CREATE TYPE plan_type AS ENUM ('FREE', 'PRO');
CREATE TYPE activity_action_type AS ENUM ('RATED', 'UPDATED', 'REMOVED');

-- ---------------------------------------------------------
-- USERS
-- ---------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150)        NOT NULL,
    username        VARCHAR(50)         NOT NULL,
    email           VARCHAR(255),
    password_hash   VARCHAR(255),
    auth_provider   auth_provider_type  NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(255),
    avatar_url      VARCHAR(500),
    bio             TEXT,
    plan            plan_type           NOT NULL DEFAULT 'FREE',
    created_at      TIMESTAMP           NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP           NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------
-- USER_STATS (1:1 com users)
-- ---------------------------------------------------------
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

-- ---------------------------------------------------------
-- CATEGORIES (as "tabelas" que o usuário vê: Jogo, Anime, Filme, Série + custom)
-- ---------------------------------------------------------
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_categories_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_categories_user_id ON categories (user_id);

-- ---------------------------------------------------------
-- WORKS (todas as obras de todas as categorias, unificadas)
-- ---------------------------------------------------------
CREATE TABLE works (
    id                  BIGSERIAL PRIMARY KEY,
    category_id         BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    image_url           VARCHAR(500),
    creator             VARCHAR(255),
    release_date        DATE,
    position            INT,
    score               DECIMAL(4,2)  NOT NULL,
    time_bonus_score    DECIMAL(4,2)  NOT NULL DEFAULT 0,
    final_score         DECIMAL(4,2)  NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT fk_works_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE,
    CONSTRAINT fk_works_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_works_category_id ON works (category_id);
CREATE INDEX idx_works_user_id ON works (user_id);
CREATE INDEX idx_works_final_score ON works (final_score DESC);

-- ---------------------------------------------------------
-- MASTER TABLE GROUPS (tabela mãe: agrupamento de categorias)
-- ---------------------------------------------------------
CREATE TABLE master_table_groups (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_master_table_groups_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE master_table_categories (
    master_table_id BIGINT NOT NULL,
    category_id      BIGINT NOT NULL,

    PRIMARY KEY (master_table_id, category_id),
    CONSTRAINT fk_mtc_master_table FOREIGN KEY (master_table_id)
        REFERENCES master_table_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_mtc_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------
-- USER_ACTIVITY_HISTORY (histórico de avaliações)
-- ---------------------------------------------------------
CREATE TABLE user_activity_history (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT                  NOT NULL,
    work_id      BIGINT                  NOT NULL,
    score_given  DECIMAL(4,2)            NOT NULL,
    action_type  activity_action_type    NOT NULL,
    created_at   TIMESTAMP               NOT NULL DEFAULT now(),

    CONSTRAINT fk_activity_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_work FOREIGN KEY (work_id)
        REFERENCES works (id) ON DELETE CASCADE
);

CREATE INDEX idx_activity_user_created ON user_activity_history (user_id, created_at DESC);

-- ---------------------------------------------------------
-- BADGES (catálogo) + USER_BADGES (join table)
-- ---------------------------------------------------------
CREATE TABLE badges (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    icon_url    VARCHAR(500),
    criteria    TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE user_badges (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    badge_id    BIGINT    NOT NULL,
    unlocked_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_badges_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_badges_badge FOREIGN KEY (badge_id)
        REFERENCES badges (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_badges UNIQUE (user_id, badge_id)
);

-- ---------------------------------------------------------
-- FOLLOW (relação social entre usuários)
-- ---------------------------------------------------------
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