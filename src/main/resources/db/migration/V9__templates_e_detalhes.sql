-- Fundação dos templates. V1–V8 continuam intactas: esta migration preserva
-- usuários, obras, notas, subdivisões e referências sociais existentes.
ALTER TABLE categories ADD COLUMN template VARCHAR(32) NOT NULL DEFAULT 'CUSTOM';
ALTER TABLE works ADD COLUMN template VARCHAR(32) NOT NULL DEFAULT 'CUSTOM';
ALTER TABLE works ADD COLUMN details JSONB NOT NULL DEFAULT '{}';

ALTER TABLE categories ADD CONSTRAINT chk_categories_template
    CHECK (template IN ('MOVIE', 'TV', 'GAME', 'BOOK', 'ANIME', 'CUSTOM'));
ALTER TABLE works ADD CONSTRAINT chk_works_template
    CHECK (template IN ('MOVIE', 'TV', 'GAME', 'BOOK', 'ANIME', 'CUSTOM'));
ALTER TABLE works ADD CONSTRAINT chk_works_details_object
    CHECK (jsonb_typeof(details) = 'object');

-- A inferência pelo rótulo existe só neste backfill. Depois da V9, o código
-- usa exclusivamente o template persistido. Emojis reconhecem tabelas cujo
-- nome foi personalizado (por exemplo, "🎬 Favoritos").
CREATE OR REPLACE FUNCTION pg_temp.legacy_template(label TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE AS $fn$
    SELECT CASE
        WHEN normalized ~ '(serie|show|\mtv\M)' AND normalized LIKE '%anime%' THEN 'CUSTOM'
        WHEN label LIKE '📦%' THEN 'CUSTOM'
        WHEN label LIKE '🎬%' THEN 'MOVIE'
        WHEN label LIKE '🎮%' THEN 'GAME'
        WHEN label LIKE '📚%' THEN 'BOOK'
        WHEN label LIKE '🎌%' OR label LIKE '🌸%' OR label LIKE '⛩%' THEN 'ANIME'
        WHEN label LIKE '📺%' THEN 'TV'
        WHEN normalized ~ '(livro|book)' THEN 'BOOK'
        WHEN normalized ~ '(jogo|game)' THEN 'GAME'
        WHEN normalized LIKE '%anime%' THEN 'ANIME'
        WHEN normalized ~ '(serie|show|\mtv\M)' THEN 'TV'
        WHEN normalized ~ '(filme|movie)' THEN 'MOVIE'
        ELSE 'CUSTOM'
    END
    FROM (SELECT translate(lower(label), 'áàâãéèêíìîóòôõúùûç', 'aaaaeeeiiioooouuuc') normalized) n;
$fn$;

CREATE TEMP TABLE legacy_mixed_tables ON COMMIT DROP AS
    SELECT c.* FROM categories c
    WHERE translate(lower(c.name), 'éèê', 'eee') ~ '(serie|show|\mtv\M)'
      AND lower(c.name) LIKE '%anime%'
      AND c.name NOT LIKE '📦%';

CREATE TEMP TABLE legacy_work_categories ON COMMIT DROP AS
    SELECT id, category_id FROM works;

UPDATE categories SET template = pg_temp.legacy_template(name);
UPDATE works w SET template = c.template FROM categories c WHERE c.id = w.category_id;

-- As capas são a única proveniência persistida no modelo antigo. Só usamos
-- hosts conhecidos; título, diretor ou duração não provam o tipo da obra.
UPDATE works SET template = 'ANIME'
    WHERE image_url ~* '^https?://([a-z0-9-]+\.)*myanimelist\.net/';
UPDATE works SET template = 'GAME'
    WHERE image_url ~* '^https?://([a-z0-9-]+\.)*rawg\.io/';
UPDATE works SET template = 'BOOK'
    WHERE image_url ~* '^https?://books\.google\.[a-z.]+/';
UPDATE works w SET template = 'TV'
    WHERE category_id IN (SELECT id FROM legacy_mixed_tables)
      AND image_url ~* '^https?://image\.tmdb\.org/';

UPDATE works SET details = details || '{"legacyClassificationRequired": true}'::jsonb
    WHERE category_id IN (SELECT id FROM legacy_mixed_tables) AND template = 'CUSTOM';

DO $migration$
DECLARE
    mixed RECORD;
    destination RECORD;
    tv_id BIGINT;
    anime_id BIGINT;
    desired_name TEXT;
    has_unclassified BOOLEAN;
BEGIN
    FOR mixed IN SELECT * FROM legacy_mixed_tables ORDER BY id LOOP
        SELECT EXISTS(SELECT 1 FROM works WHERE category_id = mixed.id
            AND template NOT IN ('TV', 'ANIME')) INTO has_unclassified;

        -- Sem itens ambíguos, a tabela original vira Séries e conserva seu ID.
        -- Com itens ambíguos, ela continua livre, com o nome e os itens intactos.
        IF NOT has_unclassified THEN
            tv_id := mixed.id;
            desired_name := CASE WHEN mixed.is_default THEN '📺 Séries'
                ELSE left(mixed.name, 87) || ' — Séries' END;
            IF EXISTS(SELECT 1 FROM categories WHERE user_id = mixed.user_id
                AND id <> mixed.id AND lower(name) = lower(desired_name)) THEN
                desired_name := left(desired_name, 75) || ' (' || mixed.id || ')';
            END IF;
            UPDATE categories SET name = desired_name, template = 'TV' WHERE id = mixed.id;
        ELSE
            tv_id := NULL;
            desired_name := CASE WHEN mixed.is_default THEN '📺 Séries'
                ELSE left(mixed.name, 87) || ' — Séries' END;
            IF mixed.is_default THEN
                SELECT id INTO tv_id FROM categories WHERE user_id = mixed.user_id
                    AND template = 'TV' AND lower(name) = lower(desired_name) ORDER BY id LIMIT 1;
            END IF;
            IF tv_id IS NULL THEN
                IF EXISTS(SELECT 1 FROM categories WHERE user_id = mixed.user_id
                    AND lower(name) = lower(desired_name)) THEN
                    desired_name := left(desired_name, 75) || ' (' || mixed.id || ')';
                END IF;
                INSERT INTO categories(user_id, name, template, is_default, created_at)
                    VALUES(mixed.user_id, desired_name, 'TV', mixed.is_default, mixed.created_at)
                    RETURNING id INTO tv_id;
            END IF;
        END IF;

        anime_id := NULL;
        desired_name := CASE WHEN mixed.is_default THEN '🎌 Animes'
            ELSE left(mixed.name, 87) || ' — Animes' END;
        IF mixed.is_default THEN
            SELECT id INTO anime_id FROM categories WHERE user_id = mixed.user_id
                AND template = 'ANIME' AND lower(name) = lower(desired_name) ORDER BY id LIMIT 1;
        END IF;
        IF anime_id IS NULL THEN
            IF EXISTS(SELECT 1 FROM categories WHERE user_id = mixed.user_id
                AND lower(name) = lower(desired_name)) THEN
                desired_name := left(desired_name, 75) || ' (' || mixed.id || ')';
            END IF;
            INSERT INTO categories(user_id, name, template, is_default, created_at)
                VALUES(mixed.user_id, desired_name, 'ANIME', mixed.is_default, mixed.created_at)
                RETURNING id INTO anime_id;
        END IF;

        FOR destination IN SELECT tv_id AS id, 'TV' AS template
            UNION ALL SELECT anime_id, 'ANIME' LOOP
            IF destination.id <> mixed.id THEN
                -- Cada obra movida continua numa subdivisão equivalente da tabela nova.
                INSERT INTO subcategories(category_id, name, created_at)
                    SELECT destination.id, s.name, s.created_at FROM subcategories s
                    WHERE s.category_id = mixed.id
                      AND EXISTS(SELECT 1 FROM works w WHERE w.subcategory_id = s.id
                          AND w.category_id = mixed.id AND w.template = destination.template)
                    ON CONFLICT DO NOTHING;
                UPDATE works w SET category_id = destination.id,
                    subcategory_id = (SELECT target.id FROM subcategories target
                        JOIN subcategories original ON original.id = w.subcategory_id
                        WHERE target.category_id = destination.id AND lower(target.name) = lower(original.name))
                    WHERE w.category_id = mixed.id AND w.template = destination.template;
            END IF;
            -- Quem selecionava a tabela mista continua vendo os itens nos mesmos grupos.
            INSERT INTO master_table_categories(master_table_id, category_id)
                SELECT master_table_id, destination.id FROM master_table_categories
                    WHERE category_id = mixed.id
                ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END;
$migration$;

-- A ordem manual usa "categoriaId:obraId". Só a categoria muda: IDs das obras,
-- notas, datas e referências no feed/takes/notificações permanecem iguais.
CREATE TEMP TABLE legacy_moved_work_keys (
    old_key TEXT PRIMARY KEY,
    new_key TEXT NOT NULL,
    user_id BIGINT NOT NULL
) ON COMMIT DROP;
INSERT INTO legacy_moved_work_keys(old_key, new_key, user_id)
    SELECT old.category_id::TEXT || ':' || w.id::TEXT,
           w.category_id::TEXT || ':' || w.id::TEXT, w.user_id
    FROM legacy_work_categories old JOIN works w ON w.id = old.id
    WHERE old.category_id <> w.category_id;

UPDATE master_table_groups g SET manual_order = (
    SELECT coalesce(jsonb_agg(coalesce((
        SELECT to_jsonb(moved.new_key) FROM legacy_moved_work_keys moved
        WHERE moved.user_id = g.user_id AND moved.old_key = entry.value #>> '{}'
    ), entry.value) ORDER BY entry.ordinality), '[]'::jsonb)
    FROM jsonb_array_elements(g.manual_order) WITH ORDINALITY entry(value, ordinality)
)
WHERE EXISTS (
    SELECT 1 FROM jsonb_array_elements(g.manual_order) entry(value)
    JOIN legacy_moved_work_keys moved ON moved.old_key = entry.value #>> '{}'
    WHERE moved.user_id = g.user_id
);
