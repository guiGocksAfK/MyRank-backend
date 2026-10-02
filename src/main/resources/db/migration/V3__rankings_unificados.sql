-- =========================================================
-- MyRank — Rankings unificados ("tabela mãe")
-- =========================================================
-- Junta várias tabelas numa lista só. A ordem manual (arrastar e soltar) fica
-- em manual_order: array JSON de "categoriaId:obraId", ex.: ["3:12", "3:8", "5:2"].
-- '[]' = sem ordem manual, cai na ordenação padrão.

CREATE TABLE master_table_groups (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    manual_order  JSONB        NOT NULL DEFAULT '[]',
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_master_table_groups_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE master_table_categories (
    master_table_id  BIGINT NOT NULL,
    category_id      BIGINT NOT NULL,

    PRIMARY KEY (master_table_id, category_id),
    CONSTRAINT fk_mtc_master_table FOREIGN KEY (master_table_id)
        REFERENCES master_table_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_mtc_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE
);
