-- =========================================================
-- MyRank — Confirmação de email no cadastro com senha
-- =========================================================
-- Sem confirmação, qualquer um cadastrava o email de outra pessoa com uma senha
-- própria; quando o dono entrasse depois pelo Google/Discord, o login social era
-- anexado a essa conta e o "dono" da senha continuava com acesso (pre-account
-- takeover). Agora conta LOCAL só entra depois de clicar no link do email, e o
-- login social só se junta a uma conta LOCAL já confirmada.
--
-- Guardamos só o SHA-256 do token (nunca o token em si), como uma senha.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verification_token_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN email_verification_expires_at TIMESTAMP;

-- Contas existentes: as sociais já tiveram o email verificado pelo Google/Discord,
-- e a única conta LOCAL em produção é do dono do projeto.
UPDATE users SET email_verified = TRUE;

CREATE UNIQUE INDEX uq_users_email_verification_token_hash
    ON users (email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;
