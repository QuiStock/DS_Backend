ALTER TABLE product ADD COLUMN unit_of_measure VARCHAR(80);
ALTER TABLE product_analysis ALTER COLUMN flow_type_id DROP NOT NULL;
ALTER TABLE suggestion ADD COLUMN desired_order_date DATE;

CREATE TABLE analysis_login_run (
  actor_id BIGINT NOT NULL REFERENCES user_account(id),
  login_id UUID NOT NULL,
  store_id BIGINT NOT NULL REFERENCES store(id),
  completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (actor_id, login_id, store_id)
);

-- Keep existing duplicates as history; all new creations are serialized on product_store.
-- A non-unique index supports the pending-action check without deleting existing data.
CREATE INDEX ix_suggestion_pending_product_store_type
  ON suggestion (store_id, product_id, type)
  WHERE status IN ('GENERATED', 'IN_EMPLOYEE_TRIAGE', 'SENT_TO_MANAGER');

DROP INDEX uq_suggestion_product_analysis;
CREATE UNIQUE INDEX uq_suggestion_product_analysis
  ON suggestion (product_analysis_id, type)
  WHERE product_analysis_id IS NOT NULL;

-- Missing ERP sales are unknown, not an observed zero.
ALTER TABLE product_store
  ALTER COLUMN sales_7d DROP NOT NULL, ALTER COLUMN sales_7d DROP DEFAULT,
  ALTER COLUMN sales_30d DROP NOT NULL, ALTER COLUMN sales_30d DROP DEFAULT;
