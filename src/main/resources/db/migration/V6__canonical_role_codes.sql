-- Keep the role codes aligned with the authentication database.
-- Preserve existing canonical roles, user assignments and permission grants.
DO $$
DECLARE
  mapping RECORD;
  old_id BIGINT;
  canonical_id BIGINT;
BEGIN
  FOR mapping IN
    SELECT * FROM (VALUES
      ('GERENTE', 'MANAGER'),
      ('GERENTE_REGIONAL', 'REGIONAL_MANAGER'),
      ('FUNCIONARIO', 'EMPLOYEE'),
      ('REPOSITOR', 'EMPLOYEE')
    ) AS codes(old_code, canonical_code)
  LOOP
    SELECT id INTO old_id FROM role WHERE code = mapping.old_code;
    SELECT id INTO canonical_id FROM role WHERE code = mapping.canonical_code;
    IF old_id IS NULL THEN
      CONTINUE;
    END IF;
    IF canonical_id IS NULL THEN
      UPDATE role SET code = mapping.canonical_code WHERE id = old_id;
    ELSE
      UPDATE user_account SET role_id = canonical_id WHERE role_id = old_id;
      INSERT INTO role_permission (role_id, permission_id)
      SELECT canonical_id, permission_id FROM role_permission WHERE role_id = old_id
      ON CONFLICT (role_id, permission_id) DO NOTHING;
      DELETE FROM role_permission WHERE role_id = old_id;
      DELETE FROM role WHERE id = old_id;
    END IF;
  END LOOP;
END;
$$;
