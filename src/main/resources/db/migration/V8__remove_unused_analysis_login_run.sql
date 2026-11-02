-- Remove the unused login/analysis table. DAU tracking is not implemented by this migration.
-- No CASCADE: unexpected dependencies must fail the migration for review.
DROP TABLE IF EXISTS public.analysis_login_run;
