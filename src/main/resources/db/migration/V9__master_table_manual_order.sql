-- =========================================================
-- MyRank — Ranking unificado: ordem manual (drag-and-drop) persistida
-- =========================================================
-- Guarda a ordem que o usuário definiu arrastando as linhas do ranking
-- unificado. Array JSON de "categoriaId:obraId", ex.: ["3:12", "3:8", "5:2"].
-- Linhas existentes recebem '[]' (sem ordem manual → cai na ordenação padrão).

ALTER TABLE master_table_groups
    ADD COLUMN manual_order JSONB NOT NULL DEFAULT '[]';
