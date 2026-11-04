-- Login no longer triggers analysis. The application uses the independent daily ml_execution job.
DROP TABLE IF EXISTS public.analysis_login_run;