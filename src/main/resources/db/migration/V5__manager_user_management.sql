-- Normalize roles and add account constraints required by manager/team APIs.
DO $$
DECLARE
  v_old_role_id BIGINT;
  v_employee_role_id BIGINT;
BEGIN
  SELECT id INTO v_old_role_id FROM role WHERE code = 'REPOSITOR';
  SELECT id INTO v_employee_role_id FROM role WHERE code = 'FUNCIONARIO';

  IF v_old_role_id IS NOT NULL AND v_employee_role_id IS NULL THEN
    UPDATE role
    SET code = 'FUNCIONARIO', name = 'FUNCIONARIO'
    WHERE id = v_old_role_id;
  ELSIF v_old_role_id IS NOT NULL AND v_employee_role_id IS NOT NULL THEN
    UPDATE user_account SET role_id = v_employee_role_id WHERE role_id = v_old_role_id;

    INSERT INTO role_permission (role_id, permission_id)
    SELECT v_employee_role_id, permission_id
    FROM role_permission
    WHERE role_id = v_old_role_id
    ON CONFLICT (role_id, permission_id) DO NOTHING;

    DELETE FROM role_permission WHERE role_id = v_old_role_id;
    DELETE FROM role WHERE id = v_old_role_id;
  END IF;
END;
$$;

INSERT INTO role (code, name) VALUES
  ('ADMIN', 'ADMIN'),
  ('GERENTE', 'GERENTE'),
  ('GERENTE_REGIONAL', 'GERENTE_REGIONAL'),
  ('FUNCIONARIO', 'FUNCIONARIO')
ON CONFLICT (code) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM user_account GROUP BY LOWER(BTRIM(email)) HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION
      'Cannot normalize account emails: duplicate values exist after trim/lowercase.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM user_store WHERE active = TRUE GROUP BY user_id HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION
      'Cannot enforce one active store per user: users with multiple active assignments exist.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM user_account u
    JOIN role r ON r.id = u.role_id
    WHERE u.status = 'ACTIVE'
      AND r.code = 'FUNCIONARIO'
      AND NOT EXISTS (
        SELECT 1 FROM user_store us WHERE us.user_id = u.id AND us.active = TRUE
      )
  ) THEN
    RAISE EXCEPTION
      'Cannot migrate active FUNCIONARIO accounts without one active store assignment.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM user_account u
    JOIN role r ON r.id = u.role_id
    WHERE u.status = 'ACTIVE'
      AND r.code = 'GERENTE_REGIONAL'
      AND NOT EXISTS (
        SELECT 1
        FROM region_manager_assignment rma
        WHERE rma.user_id = u.id AND rma.active = TRUE
      )
  ) THEN
    RAISE EXCEPTION
      'Cannot migrate active GERENTE_REGIONAL accounts without one active region assignment.';
  END IF;
END;
$$;

UPDATE user_account SET email = LOWER(BTRIM(email));

CREATE UNIQUE INDEX "uq_user_account_email_normalized"
  ON "user_account" (LOWER(BTRIM("email")));

CREATE UNIQUE INDEX "uq_user_store_one_active_per_user"
  ON "user_store" ("user_id")
  WHERE "active" = TRUE;
