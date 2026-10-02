-- "Impressão digital" do schema: colunas, tipos, padrões, constraints, índices,
-- enums e sequences, uma linha por fato, em ordem alfabética. Não depende da
-- ordem física das colunas, então serve pra provar que dois bancos têm o mesmo
-- schema mesmo quando um foi criado por migrations diferentes (ex.: o squash).
--   psql "<url>" -f deploy/schema-fingerprint.sql > banco.txt
-- Gere pros dois bancos e compare com `diff`: precisa sair vazio.
\pset format unaligned
\pset tuples_only on
SELECT line FROM (
  SELECT 'enum ' || t.typname || ' = ' || string_agg(e.enumlabel, ',' ORDER BY e.enumsortorder) AS line
  FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid
  JOIN pg_namespace n ON n.oid = t.typnamespace AND n.nspname = 'public'
  GROUP BY t.typname
  UNION ALL
  SELECT 'column ' || c.table_name || '.' || c.column_name || ' ' || c.udt_name
         || coalesce('(' || c.character_maximum_length || ')', '')
         || coalesce(' num(' || c.numeric_precision || ',' || c.numeric_scale || ')', '')
         || CASE WHEN c.is_nullable = 'NO' THEN ' NOT NULL' ELSE '' END
         || coalesce(' DEFAULT ' || c.column_default, '')
  FROM information_schema.columns c
  WHERE c.table_schema = 'public' AND c.table_name <> 'flyway_schema_history'
  UNION ALL
  SELECT 'constraint ' || cl.relname || '.' || con.conname || ' ' || pg_get_constraintdef(con.oid)
  FROM pg_constraint con JOIN pg_class cl ON cl.oid = con.conrelid
  JOIN pg_namespace n ON n.oid = cl.relnamespace AND n.nspname = 'public'
  WHERE cl.relname <> 'flyway_schema_history'
  UNION ALL
  SELECT 'index ' || indexdef FROM pg_indexes
  WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
  UNION ALL
  SELECT 'sequence ' || sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'
) f ORDER BY line;
