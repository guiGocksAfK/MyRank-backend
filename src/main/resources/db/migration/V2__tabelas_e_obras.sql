-- =========================================================
-- MyRank — Tabelas (categorias), subdivisões e obras
-- =========================================================
-- "Tabela" pra quem usa = categories aqui. Cada obra pertence a uma tabela e,
-- opcionalmente, a uma subdivisão dela, que só serve pra filtrar.

CREATE TABLE categories (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_categories_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_categories_user_id ON categories (user_id);

CREATE TABLE subcategories (
    id           BIGSERIAL    PRIMARY KEY,
    category_id  BIGINT       NOT NULL,
    name         VARCHAR(60)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_subcategories_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_subcategories_name ON subcategories (category_id, lower(name));

CREATE TABLE works (
    id                  BIGSERIAL     PRIMARY KEY,
    category_id         BIGINT        NOT NULL,
    -- Apagar a subdivisão não apaga a obra: ela só fica "sem subdivisão".
    subcategory_id      BIGINT,
    user_id             BIGINT        NOT NULL,

    title               VARCHAR(255)  NOT NULL,
    image_url           VARCHAR(500),
    creator             VARCHAR(255),
    release_date        DATE,
    time_minutes        INT           NOT NULL DEFAULT 0,

    -- Nota dada, bônus da ponderação por tempo e o resultado dos dois.
    position            INT,
    score               DECIMAL(4,2)  NOT NULL,
    time_bonus_score    DECIMAL(4,2)  NOT NULL DEFAULT 0,
    final_score         DECIMAL(4,2)  NOT NULL,

    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT fk_works_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE,
    CONSTRAINT fk_works_subcategory FOREIGN KEY (subcategory_id)
        REFERENCES subcategories (id) ON DELETE SET NULL,
    CONSTRAINT fk_works_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_works_category_id ON works (category_id);
CREATE INDEX idx_works_subcategory_id ON works (subcategory_id);
CREATE INDEX idx_works_user_id ON works (user_id);
CREATE INDEX idx_works_final_score ON works (final_score DESC);
