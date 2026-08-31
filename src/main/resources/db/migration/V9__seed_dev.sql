-- =========================================================
-- MyRank — Seed de DESENVOLVIMENTO (não usar em produção / remover antes do deploy)
--
-- Cria a conta guigocks@gmail.com já com as categorias padrão e um catálogo
-- de obras, pra não ter que recadastrar tudo depois de dropar o banco.
--
-- O login via OAuth (Discord/Google) com esse e-mail VINCULA a esta conta
-- (UserService.createOrLinkOAuthUser: acha por e-mail, seta o provider_id).
-- Como a conta já tem categorias, createDefaultCategories não roda de novo.
-- =========================================================

-- 1) Usuário + stats (1:1) --------------------------------------------------
INSERT INTO users (username, email, auth_provider, is_public, language)
VALUES ('guigo', 'guigocks@gmail.com', 'LOCAL', true, 'PT')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_stats (user_id, total_works_rated, total_hours_consumed, average_score)
SELECT id, 42, 0, 9.04
FROM users
WHERE email = 'guigocks@gmail.com'
ON CONFLICT (user_id) DO NOTHING;

-- 2) Categorias padrão ----------------------------------------------------
INSERT INTO categories (user_id, name, is_default)
SELECT u.id, c.name, true
FROM users u
CROSS JOIN (VALUES
    ('🎬 Filmes'),
    ('🎮 Jogos'),
    ('📚 Livros'),
    ('📺 Séries & Animes')
) AS c(name)
WHERE u.email = 'guigocks@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM categories x WHERE x.user_id = u.id AND x.name = c.name
  );

-- 3) Obras --------------------------------------------------------------
-- helper implícito: cada bloco resolve (category_id, user_id) por subquery
-- e só insere o que ainda não existe naquela categoria.

-- 3a) Séries & Animes
INSERT INTO works (category_id, user_id, title, position, score, final_score)
SELECT cat.id, cat.user_id, w.title, w.position, w.score, w.score
FROM (
    SELECT c.id, c.user_id
    FROM categories c JOIN users u ON u.id = c.user_id
    WHERE u.email = 'guigocks@gmail.com' AND c.name = '📺 Séries & Animes'
) AS cat
CROSS JOIN (VALUES
    ('One Piece',          1, 10.0),
    ('Attack on Titan',    2, 10.0),
    ('Game of Thrones',    3,  9.7),
    ('Breaking Bad',       4,  9.7),
    ('Stranger Things',    5,  9.0),
    ('La casa de papel',   6,  8.9),
    ('Sherlock Holmes',    7,  8.9),
    ('Dragon Ball',        8,  8.8),
    ('Round 6',            9,  8.0)
) AS w(title, position, score)
WHERE NOT EXISTS (
    SELECT 1 FROM works ex WHERE ex.category_id = cat.id AND ex.title = w.title
);

-- 3b) Jogos
INSERT INTO works (category_id, user_id, title, position, score, final_score)
SELECT cat.id, cat.user_id, w.title, w.position, w.score, w.score
FROM (
    SELECT c.id, c.user_id
    FROM categories c JOIN users u ON u.id = c.user_id
    WHERE u.email = 'guigocks@gmail.com' AND c.name = '🎮 Jogos'
) AS cat
CROSS JOIN (VALUES
    ('Red Dead Redemption 2',        1, 10.0),
    ('The Last of Us',               2,  9.7),
    ('Sekiro',                       3,  9.6),
    ('Marvel''s Spider-Man',         4,  9.3),
    ('Hollow Knight',                5,  9.3),
    ('Hogwarts Legacy',              6,  8.9),
    ('Zelda: A Link to the Past',    7,  8.8),
    ('Pokémon Alpha Sapphire',       8,  8.8),
    ('Pokémon FireRed',              9,  8.7),
    ('Undertale',                   10,  8.7),
    ('Pokémon HeartGold',           11,  8.6),
    ('Pokémon White',               12,  8.5),
    ('Pokémon Platinum',            13,  8.3),
    ('Pokémon White 2',             14,  8.2),
    ('FNaF: Into the Pit',          15,  8.0),
    ('Bendy and the Ink Machine',   16,  7.8)
) AS w(title, position, score)
WHERE NOT EXISTS (
    SELECT 1 FROM works ex WHERE ex.category_id = cat.id AND ex.title = w.title
);

-- 3c) Filmes
INSERT INTO works (category_id, user_id, title, position, score, final_score)
SELECT cat.id, cat.user_id, w.title, w.position, w.score, w.score
FROM (
    SELECT c.id, c.user_id
    FROM categories c JOIN users u ON u.id = c.user_id
    WHERE u.email = 'guigocks@gmail.com' AND c.name = '🎬 Filmes'
) AS cat
CROSS JOIN (VALUES
    ('O Senhor dos Anéis: O Retorno do Rei',        1, 9.9),
    ('Interestelar',                                 2, 9.7),
    ('O Cavaleiro das Trevas',                       3, 9.7),
    ('O Senhor dos Anéis: A Sociedade do Anel',      4, 9.6),
    ('O Poderoso Chefão',                            5, 9.6),
    ('O Senhor dos Anéis: As Duas Torres',           6, 9.5),
    ('A Origem',                                     7, 9.4),
    ('Um Sonho de Liberdade',                        8, 9.4),
    ('O Poderoso Chefão II',                         9, 9.3),
    ('2001: Uma Odisseia no Espaço',               10, 9.2),
    ('O Grande Truque',                            11, 9.1),
    ('Batman Begins',                              12, 8.9),
    ('Homem-Aranha: Um Novo Dia',                  13, 8.8),
    ('A Chegada',                                  14, 8.6),
    ('Wall-E',                                     15, 8.6),
    ('Duna',                                       16, 8.1),
    ('Gran Torino',                               17, 8.0)
) AS w(title, position, score)
WHERE NOT EXISTS (
    SELECT 1 FROM works ex WHERE ex.category_id = cat.id AND ex.title = w.title
);
