-- =========================================================
-- MyRank — Limpeza de colunas e tabela legadas
-- =========================================================
-- Remove a tabela user_stats (sem uso) e os tokens por link de email dos
-- fluxos antigos de confirmação de cadastro e redefinição de senha (substituídos
-- pelos códigos de 6 dígitos em memória + passe curto).

DROP TABLE IF EXISTS user_stats;

DROP INDEX IF EXISTS uq_users_email_verification_token_hash;
DROP INDEX IF EXISTS uq_users_password_reset_token_hash;

ALTER TABLE users
    DROP COLUMN IF EXISTS email_verification_token_hash,
    DROP COLUMN IF EXISTS email_verification_expires_at,
    DROP COLUMN IF EXISTS password_reset_token_hash,
    DROP COLUMN IF EXISTS password_reset_expires_at;
