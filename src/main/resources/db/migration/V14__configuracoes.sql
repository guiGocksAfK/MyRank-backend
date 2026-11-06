-- Configurações da conta. dm_policy (EVERYONE/FOLLOWING) e os grupos de aviso são
-- validados no código, sem CHECK (decisoes.md).

-- Quem pode abrir conversa direta com a pessoa.
ALTER TABLE users ADD COLUMN dm_policy VARCHAR(20) NOT NULL DEFAULT 'EVERYONE';

-- Grupos de aviso silenciados, separados por vírgula (ex.: "REACTIONS,TAKES").
-- Vazio = recebe tudo.
ALTER TABLE users ADD COLUMN muted_notifications VARCHAR(200) NOT NULL DEFAULT '';

-- Nota ponderada pelo tempo já ligada ao abrir a Dashboard e os Rankings.
ALTER TABLE users ADD COLUMN weighted_by_default BOOLEAN NOT NULL DEFAULT FALSE;
