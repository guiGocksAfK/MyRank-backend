-- Contas existentes já concluíram o tutorial; cadastros novos recebem TABLES no código.
ALTER TABLE users ADD COLUMN onboarding_step VARCHAR(20) NOT NULL DEFAULT 'DONE';
ALTER TABLE users ALTER COLUMN is_public SET DEFAULT false;
