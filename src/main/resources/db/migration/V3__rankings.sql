-- =========================================================
-- MyRank — Rankings: categorias ("tabelas"), obras e tabelas-mãe
-- =========================================================

-- CATEGORIES (as "tabelas" que o usuário vê: Jogo, Anime, Filme, Série + custom)
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

-- WORKS (todas as obras de todas as categorias, unificadas)
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

-- MASTER TABLE GROUPS (tabela mãe: agrupamento de categorias)
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
