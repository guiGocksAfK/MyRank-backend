-- =========================================================
-- MyRank — Vínculo com o Discord (bot)
-- =========================================================
-- Coluna própria em vez de reusar (auth_provider, provider_id): o `sub` do Google
-- também é numérico, e distinguir de snowflake por quantidade de dígitos é
-- heurística que quebra silenciosamente. Separar "como faço login" de "qual é o
-- meu Discord" permite ter os dois ao mesmo tempo.

ALTER TABLE users ADD COLUMN discord_id VARCHAR(32);

-- Backfill só do caso inequívoco: quem se cadastrou PELO Discord. Contas híbridas
-- (LOCAL/GOOGLE que já logaram com Discord) preenchem o campo ao entrar uma vez
-- pelo Discord depois do deploy — o OAuthService/UserService grava sempre.
UPDATE users
SET discord_id = provider_id
WHERE auth_provider = 'DISCORD'
  AND provider_id IS NOT NULL
  AND provider_id <> '';

CREATE UNIQUE INDEX uq_users_discord_id
    ON users (discord_id)
    WHERE discord_id IS NOT NULL;
