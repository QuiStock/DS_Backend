-- V12: remove the unused login/analysis table.
-- No CASCADE: unexpected dependencies must stop the migration for review.
DROP TABLE IF EXISTS public.analysis_login_run;

-- Keep the catalog auditable without presenting the removed object as current.
DO $migration$
BEGIN
  IF to_regclass('governance.data_catalog_table') IS NOT NULL THEN
    UPDATE governance.data_catalog_table
       SET is_present = FALSE,
           description = 'Objeto removido do schema public por não ser utilizado pela aplicação; registro mantido apenas como histórico do catálogo.',
           business_rules = 'Tabela removida; os registros eram demonstrativos e não representavam acessos reais. O requisito de DAU permanece pendente.',
           access_policy = 'N/A — objeto removido do schema public.',
           reviewed_at = CURRENT_TIMESTAMP
     WHERE schema_name = 'public'
       AND table_name = 'analysis_login_run';
  END IF;

  IF to_regclass('governance.data_catalog_column') IS NOT NULL THEN
    UPDATE governance.data_catalog_column
       SET is_present = FALSE,
           business_rules = 'Coluna pertencente a uma tabela removida; metadado preservado apenas para histórico. DAU não está implementado.',
           access_policy = 'N/A — coluna de objeto removido.',
           reviewed_at = CURRENT_TIMESTAMP
     WHERE schema_name = 'public'
       AND table_name = 'analysis_login_run';
  END IF;
END;
$migration$;
