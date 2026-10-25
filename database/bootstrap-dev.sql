BEGIN;

INSERT INTO "role" ("code", "name")
VALUES
  ('REGIONAL_MANAGER', 'Gerente Regional'),
  ('EMPLOYEE', 'Funcionário')
ON CONFLICT DO NOTHING;

INSERT INTO "user_account" ("role_id", "name", "email", "password_hash", "status")
SELECT
  role_row."id",
  seed."name",
  seed."email",
  seed."password_hash",
  'ACTIVE'::"user_status"
FROM (
  VALUES
    ('REGIONAL_MANAGER', 'Usuário Demo Gerente Regional', 'gerente.regional@quistock.test', '$2b$12$TU1Nsgb40nSKJVHgFI.mtOfiJfVRcfA6lr0Q/qU5NggbrgyHkEmNC'),
    ('EMPLOYEE', 'Usuário Demo Funcionário', 'repositor@quistock.test', '$2b$12$6HwO.LUpStlleUjmzLxjauDqp/md3nVYqLPPXpY05mSbn2RqL5eMG')
) AS seed ("role_code", "name", "email", "password_hash")
JOIN "role" AS role_row ON role_row."code" = seed."role_code"
ON CONFLICT ("email") DO NOTHING;

COMMIT;
