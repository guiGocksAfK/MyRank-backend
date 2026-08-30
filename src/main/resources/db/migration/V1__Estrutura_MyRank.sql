-- =========================================================
-- MyRank - Schema inicial
-- V1__Estrutura_MyRank.sql
-- =========================================================

-- ---------------------------------------------------------
-- ENUM TYPES
-- ---------------------------------------------------------
CREATE TYPE auth_provider_type AS ENUM ('LOCAL', 'GOOGLE', 'DISCORD');
CREATE TYPE plan_type AS ENUM ('FREE', 'PRO');
CREATE TYPE feed_event_type AS ENUM ('RATED', 'ADDED', 'BADGE', 'TAKE');
CREATE TYPE reaction_kind AS ENUM ('UP', 'AGREE', 'DISAGREE');
CREATE TYPE notification_type AS ENUM ('REACTION', 'FOLLOW', 'TAKE');
CREATE TYPE conversation_type AS ENUM ('DIRECT', 'GROUP');
CREATE TYPE conversation_member_role AS ENUM ('OWNER', 'MEMBER');
CREATE TYPE message_kind AS ENUM ('USER', 'SYSTEM');

-- ---------------------------------------------------------
-- USERS
-- ---------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)         NOT NULL,
    email           VARCHAR(255),
    password_hash   VARCHAR(255),
    auth_provider   auth_provider_type  NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(255),
    avatar_url      VARCHAR(500),
    bio             TEXT,
    plan            plan_type           NOT NULL DEFAULT 'FREE',
    is_public       BOOLEAN             NOT NULL DEFAULT true,
    language        VARCHAR(5)          NOT NULL DEFAULT 'PT',   -- idioma da interface (PT | EN | ES)
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

-- ---------------------------------------------------------
-- USER_AVATARS (1:1 com users) — foto de perfil enviada pelo usuário.
-- Tabela separada pra não carregar o BYTEA em toda request autenticada.
-- ---------------------------------------------------------
CREATE TABLE user_avatars (
    user_id       BIGINT PRIMARY KEY,
    image         BYTEA        NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
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
    time_minutes        INT           NOT NULL DEFAULT 0,
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
-- BADGES (catálogo) + USER_BADGES (join table)
-- ---------------------------------------------------------
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

-- ---------------------------------------------------------
-- SOCIAL: TAKES + FEED_EVENTS + FEED_REACTIONS
-- ---------------------------------------------------------

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

-- ---------------------------------------------------------
-- NOTIFICATIONS
-- Reações são AGREGADAS: 1 linha por (destinatário, feed_event, reaction_kind),
-- com actor_count e o último ator. Follow e take são 1 linha por evento.
-- ---------------------------------------------------------
CREATE TABLE notifications (
    id             BIGSERIAL         PRIMARY KEY,
    user_id        BIGINT            NOT NULL,       -- destinatário
    type           notification_type NOT NULL,
    actor_id       BIGINT,                           -- último/único ator
    actor_count    INT               NOT NULL DEFAULT 1,
    feed_event_id  BIGINT,
    reaction_kind  reaction_kind,
    read           BOOLEAN           NOT NULL DEFAULT false,
    created_at     TIMESTAMP         NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP         NOT NULL DEFAULT now(),

    CONSTRAINT fk_notif_user  FOREIGN KEY (user_id)       REFERENCES users (id)       ON DELETE CASCADE,
    CONSTRAINT fk_notif_actor FOREIGN KEY (actor_id)      REFERENCES users (id)       ON DELETE CASCADE,
    CONSTRAINT fk_notif_event FOREIGN KEY (feed_event_id) REFERENCES feed_events (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user ON notifications (user_id, updated_at DESC);
CREATE UNIQUE INDEX uq_notif_reaction ON notifications (user_id, feed_event_id, reaction_kind) WHERE type = 'REACTION';
CREATE UNIQUE INDEX uq_notif_follow   ON notifications (user_id, actor_id) WHERE type = 'FOLLOW';
CREATE UNIQUE INDEX uq_notif_take     ON notifications (user_id, feed_event_id) WHERE type = 'TAKE';

-- ---------------------------------------------------------
-- CHAT — conversas (DM + grupo) unificadas.
-- DIRECT = conversa de 2 membros (DM). GROUP = nome + N membros + 1 OWNER.
-- Não-lidas por membro via cursor last_read_message_id.
-- Real-time via polling (igual ao sininho).
-- ---------------------------------------------------------
CREATE TABLE conversations (
    id          BIGSERIAL         PRIMARY KEY,
    type        conversation_type NOT NULL,
    name        VARCHAR(80),                          -- null em DIRECT
    created_by  BIGINT            NOT NULL,
    created_at  TIMESTAMP         NOT NULL DEFAULT now(),

    CONSTRAINT fk_conversations_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE CASCADE
);

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

CREATE TABLE messages (
    id              BIGSERIAL     PRIMARY KEY,
    conversation_id BIGINT        NOT NULL,
    sender_id       BIGINT        NOT NULL,
    kind            message_kind  NOT NULL DEFAULT 'USER',   -- SYSTEM = "fulano criou o grupo" etc
    body            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, id DESC);

-- ---------------------------------------------------------
-- AI_INSIGHTS — analise de perfil gerada por IA (Gemini).
-- payload = JSON estruturado devolvido pelo modelo (summaryTitle, traits,
-- tasteProfile, recommendation...). Cacheado por (user_id, selection_hash):
-- mesma selecao de obras + mesmas notas + mesmo modelo => reaproveita a linha.
-- ---------------------------------------------------------
CREATE TABLE ai_insights (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    selection_hash  VARCHAR(64)  NOT NULL,   -- SHA-256 de (modelo + obras ordenadas + notas)
    model           VARCHAR(60)  NOT NULL,
    work_count      INT          NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_ai_insights_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_insights_selection UNIQUE (user_id, selection_hash)
);

CREATE INDEX idx_ai_insights_user_created ON ai_insights (user_id, created_at DESC);