-- Definições dos campos próprios de cada tabela; tipos e limites são validados no código.
ALTER TABLE categories
    ADD COLUMN custom_fields JSONB NOT NULL DEFAULT '[]',
    ADD CONSTRAINT categories_custom_fields_array CHECK (jsonb_typeof(custom_fields) = 'array');
