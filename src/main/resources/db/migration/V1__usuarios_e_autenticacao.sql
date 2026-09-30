-- =========================================================
-- MyRank — Usuários e autenticação
-- =========================================================
-- Conta, formas de login (senha, Google, Discord), os códigos e tokens de
-- segurança da conta e as estatísticas agregadas de cada usuário.

CREATE TYPE auth_provider_type AS ENUM ('LOCAL', 'GOOGLE', 'DISCORD');
CREATE TYPE plan_type          AS ENUM ('FREE', 'PRO');

CREATE TABLE users (
    id              BIGSERIAL           PRIMARY KEY,
    username        VARCHAR(50)         NOT NULL,
    email           VARCHAR(255),

    -- Login. Conta social não tem senha. discord_id é separado de provider_id
    -- porque "como a pessoa entra" e "qual é o Discord dela" são coisas
    -- diferentes: uma conta com senha pode ter Discord vinculado (bot).
    password_hash   VARCHAR(255),
    auth_provider   auth_provider_type  NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(255),
    discord_id      VARCHAR(32),

    -- Conta LOCAL só entra com email confirmado; as sociais já nascem true.
    email_verified                  BOOLEAN     NOT NULL DEFAULT false,
    -- Tokens e códigos de uso único: guardamos só o SHA-256 (hex), nunca o valor.
    email_verification_token_hash   VARCHAR(64),
    email_verification_expires_at   TIMESTAMP,
    password_reset_token_hash       VARCHAR(64),
    password_reset_expires_at       TIMESTAMP,
    account_deletion_code_hash      VARCHAR(64),
    account_deletion_code_expires_at TIMESTAMP,
    -- Vai dentro de cada token de login; aumentar (troca de senha) derruba
    -- as sessões abertas em todos os aparelhos.
    token_version   INTEGER             NOT NULL DEFAULT 0,

    -- Perfil e preferências
    avatar_url      VARCHAR(1000),                           -- upload do usuário ou avatar do OAuth
    bio             TEXT,
    plan            plan_type           NOT NULL DEFAULT 'FREE',
    is_public       BOOLEAN             NOT NULL DEFAULT true,
    language        VARCHAR(5)          NOT NULL DEFAULT 'PT', -- idioma da interface (PT | EN | ES)

    last_seen_at    TIMESTAMP,                               -- presença (heartbeat do chat)
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

CREATE UNIQUE INDEX uq_users_discord_id
    ON users (discord_id)
    WHERE discord_id IS NOT NULL;

CREATE UNIQUE INDEX uq_users_email_verification_token_hash
    ON users (email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;

CREATE UNIQUE INDEX uq_users_password_reset_token_hash
    ON users (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;

-- Estatísticas agregadas (1:1 com users)
CREATE TABLE user_stats (
    id                      BIGSERIAL     PRIMARY KEY,
    user_id                 BIGINT        UNIQUE NOT NULL,
    total_works_rated       INT           NOT NULL DEFAULT 0,
    total_hours_consumed    DECIMAL(10,2) NOT NULL DEFAULT 0,
    average_score           DECIMAL(4,2)  NOT NULL DEFAULT 0,
    updated_at              TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_stats_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);
