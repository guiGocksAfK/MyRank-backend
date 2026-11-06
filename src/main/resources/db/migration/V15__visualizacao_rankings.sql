-- Visualização padrão dos Rankings (LIST/GRID), validada no código, sem CHECK (decisoes.md).
ALTER TABLE users ADD COLUMN rankings_view VARCHAR(10) NOT NULL DEFAULT 'LIST';
