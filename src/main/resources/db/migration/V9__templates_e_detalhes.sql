-- =========================================================
-- MyRank — Templates das tabelas e detalhes dos itens
-- =========================================================
-- Template = o tipo do conteúdo (filme, série, anime, jogo, livro, ou
-- personalizado). Ele decide a busca, o card e as conquistas; o nome e o emoji
-- da tabela viram só apresentação.
--
-- Uma tabela tem UM OU MAIS templates (ex.: Jogos + Séries + Animes), na ordem
-- em que a pessoa escolheu. Cada item guarda o próprio template, que precisa ser
-- um dos templates da tabela. Os valores possíveis são validados no código
-- (enum TableTemplate), não aqui: template novo não exige migration.

CREATE TABLE category_templates (
    category_id  BIGINT       NOT NULL,
    position     INTEGER      NOT NULL,
    template     VARCHAR(32)  NOT NULL,

    PRIMARY KEY (category_id, position),
    CONSTRAINT uq_category_templates UNIQUE (category_id, template),
    CONSTRAINT fk_category_templates_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE
);

ALTER TABLE works
    ADD COLUMN template VARCHAR(32) NOT NULL DEFAULT 'CUSTOM',
    -- Dados específicos do template (ex.: de onde o item veio na API). Sempre um objeto.
    ADD COLUMN details  JSONB       NOT NULL DEFAULT '{}',
    ADD CONSTRAINT chk_works_details_object CHECK (jsonb_typeof(details) = 'object');

-- ---------------------------------------------------------
-- Tabelas existentes: classificadas UMA vez pelo nome antigo (emoji primeiro,
-- depois palavras). Depois daqui o nome nunca mais decide nada.
-- A antiga "Séries & Animes" vira uma tabela mista com Séries + Animes.
-- ---------------------------------------------------------
CREATE TEMP TABLE legacy_category_label ON COMMIT DROP AS
SELECT id,
       name,
       translate(lower(name), 'áàâãéèêíìîóòôõúùûç', 'aaaaeeeiiioooouuuc') AS plain
FROM categories;

INSERT INTO category_templates (category_id, position, template)
SELECT id, 0, 'TV' FROM legacy_category_label
WHERE plain ~ '(serie|show|\mtv\M)' AND plain LIKE '%anime%'
UNION ALL
SELECT id, 1, 'ANIME' FROM legacy_category_label
WHERE plain ~ '(serie|show|\mtv\M)' AND plain LIKE '%anime%';

INSERT INTO category_templates (category_id, position, template)
SELECT id, 0, CASE
        WHEN name LIKE '📦%' THEN 'CUSTOM'
        WHEN name LIKE '🎬%' THEN 'MOVIE'
        WHEN name LIKE '🎮%' THEN 'GAME'
        WHEN name LIKE '📚%' THEN 'BOOK'
        WHEN name LIKE '🎌%' OR name LIKE '🌸%' THEN 'ANIME'
        WHEN name LIKE '📺%' THEN 'TV'
        WHEN plain ~ '(livro|book)' THEN 'BOOK'
        WHEN plain ~ '(jogo|game)' THEN 'GAME'
        WHEN plain LIKE '%anime%' THEN 'ANIME'
        WHEN plain ~ '(serie|show|\mtv\M)' THEN 'TV'
        WHEN plain ~ '(filme|movie)' THEN 'MOVIE'
        ELSE 'CUSTOM'
    END
FROM legacy_category_label l
WHERE NOT EXISTS (SELECT 1 FROM category_templates t WHERE t.category_id = l.id);

-- Itens herdam o primeiro template da tabela. Na tabela mista, a capa diz o
-- que é: MyAnimeList = anime; o resto (TMDB ou sem capa) fica como série.
UPDATE works w
SET template = t.template
FROM category_templates t
WHERE t.category_id = w.category_id AND t.position = 0;

UPDATE works w
SET template = 'ANIME'
WHERE w.image_url ~* '^https?://([a-z0-9-]+\.)*myanimelist\.net/'
  AND EXISTS (SELECT 1 FROM category_templates t
              WHERE t.category_id = w.category_id AND t.template = 'ANIME');
