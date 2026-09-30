-- =========================================================
-- MyRank — Versão de sessão por usuário
-- =========================================================
-- Cada token de login carrega a versão da conta; se ela mudar (troca de senha),
-- todo token antigo deixa de valer em qualquer aparelho. Tokens emitidos antes
-- desta coluna não têm a versão e contam como 0, então ninguém cai no deploy.

ALTER TABLE users
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
