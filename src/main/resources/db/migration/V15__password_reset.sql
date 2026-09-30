-- =========================================================
-- MyRank — "Esqueci minha senha"
-- =========================================================
-- Link de uso único mandado por email, válido por 15 minutos. Como na
-- confirmação de email (V12), guardamos só o SHA-256 do token.

ALTER TABLE users
    ADD COLUMN password_reset_token_hash VARCHAR(64),
    ADD COLUMN password_reset_expires_at TIMESTAMP;

CREATE UNIQUE INDEX uq_users_password_reset_token_hash
    ON users (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;
