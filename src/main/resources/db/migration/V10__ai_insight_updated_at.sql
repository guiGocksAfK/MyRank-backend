-- =========================================================
-- MyRank — AI Insights: quando a análise foi (re)gerada
-- =========================================================
-- A linha em ai_insights é reaproveitada quando o hash de seleção não muda,
-- então "Gerar novamente" mantém o mesmo id e o created_at antigo. Este
-- updated_at guarda o momento da última geração de verdade — é o que o front
-- mostra em "gerado há X". Chat de follow-up NÃO altera esse campo.

ALTER TABLE ai_insights ADD COLUMN updated_at TIMESTAMP;
UPDATE ai_insights SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE ai_insights ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE ai_insights ALTER COLUMN updated_at SET DEFAULT now();
