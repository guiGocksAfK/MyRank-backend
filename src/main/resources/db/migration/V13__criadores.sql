-- Fotos dos criadores (tela de Criadores). kind (PERSON/COMPANY) e creator_type
-- (Diretor/Escritor/Studio/Criador) são validados no código, sem CHECK (decisoes.md).

-- Foto automática de cada criador, achada na TMDB (pessoa = foto de perfil, estúdio =
-- logo). Compartilhada entre todos os usuários: a do Christopher Nolan é a mesma pra
-- todo mundo, então a TMDB só é consultada uma vez por nome. photo_url nulo = procurou
-- e não achou (não procura de novo antes de fetched_at + 30 dias).
CREATE TABLE creator_photos (
    kind         VARCHAR(10)  NOT NULL,
    creator_key  VARCHAR(200) NOT NULL,
    photo_url    VARCHAR(500),
    fetched_at   TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_creator_photos PRIMARY KEY (kind, creator_key)
);

-- O que cada usuário editou num criador: a foto (link próprio) e/ou o tipo. Vale só
-- pra ele. creator_key é o nome normalizado (CreatorNames.key).
CREATE TABLE user_creators (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    creator_key   VARCHAR(200)  NOT NULL,
    photo_url     VARCHAR(1000),
    creator_type  VARCHAR(20),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_creators UNIQUE (user_id, creator_key),
    CONSTRAINT fk_user_creators_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);
