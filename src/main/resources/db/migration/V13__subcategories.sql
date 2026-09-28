-- =========================================================
-- MyRank — Subcategorias dentro de uma tabela
-- =========================================================
-- Divide uma tabela (ex.: "Séries & Animes" → Animes / TV Shows) sem criar outra:
-- as obras continuam na mesma categoria e a subcategoria só serve pra filtrar.
-- Cada obra fica em no máximo uma subcategoria.

CREATE TABLE subcategories (
    id           BIGSERIAL    PRIMARY KEY,
    category_id  BIGINT       NOT NULL,
    name         VARCHAR(60)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_subcategories_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_subcategories_name ON subcategories (category_id, lower(name));

-- Apagar a subcategoria não apaga obra nenhuma: ela só volta a ficar "sem subcategoria".
ALTER TABLE works ADD COLUMN subcategory_id BIGINT;
ALTER TABLE works ADD CONSTRAINT fk_works_subcategory FOREIGN KEY (subcategory_id)
    REFERENCES subcategories (id) ON DELETE SET NULL;

CREATE INDEX idx_works_subcategory_id ON works (subcategory_id);
