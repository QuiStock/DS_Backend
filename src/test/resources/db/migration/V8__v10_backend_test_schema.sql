-- Test-only schema evolution for the PostgreSQL Testcontainers database.
-- Production schema changes are delivered separately in entrega modelagem.

ALTER TABLE sale_item
  DROP CONSTRAINT IF EXISTS sale_item_product_id_fkey,
  DROP CONSTRAINT IF EXISTS chk_sale_item_quantity;
DROP INDEX IF EXISTS idx_sale_item_sale_product_batch;
ALTER TABLE sale_item DROP COLUMN product_id;
ALTER TABLE sale_item
  ADD CONSTRAINT chk_sale_item_nonnegative_quantity CHECK (quantity >= 0),
  ADD CONSTRAINT chk_sale_item_zero_quantity_amount
    CHECK (quantity <> 0 OR total_amount = 0);
CREATE INDEX idx_sale_item_sale_batch ON sale_item (sale_id, batch_id);

ALTER TABLE inventory_movement
  DROP CONSTRAINT IF EXISTS inventory_movement_store_id_fkey,
  DROP CONSTRAINT IF EXISTS inventory_movement_product_id_fkey,
  DROP CONSTRAINT IF EXISTS fk_inventory_movement_batch_product_store,
  DROP CONSTRAINT IF EXISTS fk_inventory_movement_sale_item_context,
  DROP CONSTRAINT IF EXISTS chk_inventory_movement_quantity;
ALTER TABLE inventory_movement
  ALTER COLUMN batch_id DROP NOT NULL,
  DROP COLUMN product_id,
  DROP COLUMN store_id;
UPDATE inventory_movement
SET batch_id = NULL
WHERE sale_item_id IS NOT NULL;
ALTER TABLE inventory_movement
  ADD CONSTRAINT chk_inventory_movement_source CHECK (
    (batch_id IS NOT NULL AND sale_item_id IS NULL)
    OR (batch_id IS NULL AND sale_item_id IS NOT NULL)
  ),
  ADD CONSTRAINT chk_inventory_movement_nonnegative_quantity CHECK (quantity >= 0);

ALTER TABLE product_analysis
  ADD COLUMN classifier VARCHAR(100),
  ADD COLUMN analysis_status VARCHAR(40),
  ADD COLUMN reason TEXT,
  ADD COLUMN daily_sales_average NUMERIC(14,6),
  ADD COLUMN stock_coverage_days NUMERIC(14,6),
  ADD COLUMN current_stock NUMERIC(14,3),
  ADD COLUMN minimum_stock NUMERIC(14,3),
  ADD COLUMN sales_7d NUMERIC(14,3),
  ADD COLUMN sales_30d NUMERIC(14,3),
  ADD COLUMN expiration_days INTEGER,
  ADD COLUMN supplier_lead_time_days INTEGER;
UPDATE product_analysis pa
SET classifier = pa.context ->> 'classifier',
    analysis_status = pa.context ->> 'analysis_status',
    reason = pa.context ->> 'reason',
    daily_sales_average = NULLIF(pa.metrics ->> 'daily_sales_average', 'null')::NUMERIC(14,6),
    stock_coverage_days = NULLIF(pa.metrics ->> 'stock_coverage_days', 'null')::NUMERIC(14,6),
    current_stock = NULLIF(pa.metrics ->> 'current_stock', 'null')::NUMERIC(14,3),
    minimum_stock = NULLIF(pa.metrics ->> 'minimum_stock', 'null')::NUMERIC(14,3),
    sales_7d = NULLIF(pa.metrics ->> 'sales_7d', 'null')::NUMERIC(14,3),
    sales_30d = NULLIF(pa.metrics ->> 'sales_30d', 'null')::NUMERIC(14,3),
    expiration_days = NULLIF(pa.metrics ->> 'expiration_days', 'null')::INTEGER,
    supplier_lead_time_days = COALESCE(
      NULLIF(pa.metrics ->> 'supplier_lead_time_days', 'null')::INTEGER,
      NULLIF(pa.metrics ->> 'supplier_lead_time', 'null')::INTEGER
    ),
    context = NULLIF(
      (CASE
         WHEN pa.context IS NULL THEN '{}'::JSONB
         WHEN jsonb_typeof(pa.context) = 'object'
           THEN pa.context - ARRAY['classifier','analysis_status','reason']::TEXT[]
         ELSE jsonb_build_object('legacy_context', pa.context)
       END)
      || (CASE
            WHEN jsonb_typeof(pa.metrics) = 'object'
              AND (pa.metrics - ARRAY[
                'daily_sales_average','stock_coverage_days','current_stock','minimum_stock',
                'sales_7d','sales_30d','expiration_days',
                'supplier_lead_time_days','supplier_lead_time'
              ]::TEXT[]) <> '{}'::JSONB
              THEN jsonb_build_object(
                'legacy_metrics',
                pa.metrics - ARRAY[
                  'daily_sales_average','stock_coverage_days','current_stock','minimum_stock',
                  'sales_7d','sales_30d','expiration_days',
                  'supplier_lead_time_days','supplier_lead_time'
                ]::TEXT[]
              )
            WHEN jsonb_typeof(pa.metrics) <> 'object'
              THEN jsonb_build_object('legacy_metrics', pa.metrics)
            ELSE '{}'::JSONB
          END),
      '{}'::JSONB
    );
ALTER TABLE product_analysis
  DROP COLUMN metrics,
  ALTER COLUMN flow_type_id DROP NOT NULL,
  ADD CONSTRAINT chk_product_analysis_status
    CHECK (analysis_status IS NULL OR analysis_status IN ('ANALYZED', 'INSUFFICIENT_DATA')),
  ADD CONSTRAINT chk_product_analysis_nonnegative_metrics CHECK (
    (daily_sales_average IS NULL OR daily_sales_average >= 0)
    AND (stock_coverage_days IS NULL OR stock_coverage_days >= 0)
    AND (current_stock IS NULL OR current_stock >= 0)
    AND (minimum_stock IS NULL OR minimum_stock >= 0)
    AND (sales_7d IS NULL OR sales_7d >= 0)
    AND (sales_30d IS NULL OR sales_30d >= 0)
    AND (supplier_lead_time_days IS NULL OR supplier_lead_time_days >= 0)
  );

ALTER TABLE suggestion ADD COLUMN product_store_id BIGINT;
UPDATE suggestion s
SET product_store_id = ps.id
FROM product_store ps
WHERE s.product_analysis_id IS NULL
  AND ps.product_id = s.product_id
  AND ps.store_id = s.store_id;
UPDATE suggestion
SET product_store_id = NULL
WHERE product_analysis_id IS NOT NULL;
DROP INDEX IF EXISTS ix_suggestion_pending_product_store_type;
DROP INDEX IF EXISTS uq_suggestion_product_analysis;
ALTER TABLE suggestion
  DROP CONSTRAINT IF EXISTS suggestion_product_id_fkey,
  DROP CONSTRAINT IF EXISTS suggestion_store_id_fkey,
  DROP COLUMN product_id,
  DROP COLUMN store_id,
  ADD CONSTRAINT chk_suggestion_source_reference CHECK (
    (product_analysis_id IS NOT NULL AND product_store_id IS NULL)
    OR (product_analysis_id IS NULL AND product_store_id IS NOT NULL)
  ),
  ADD CONSTRAINT chk_suggestion_ml_source
    CHECK (origin <> 'ML' OR product_analysis_id IS NOT NULL),
  ADD CONSTRAINT suggestion_product_store_id_fkey
    FOREIGN KEY (product_store_id) REFERENCES product_store(id)
    DEFERRABLE INITIALLY IMMEDIATE;
CREATE INDEX ix_suggestion_pending_product_store_type
  ON suggestion (product_store_id, type)
  WHERE product_store_id IS NOT NULL
    AND status IN ('GENERATED', 'IN_EMPLOYEE_TRIAGE', 'SENT_TO_MANAGER');
CREATE UNIQUE INDEX uq_suggestion_product_analysis
  ON suggestion (product_analysis_id, type)
  WHERE product_analysis_id IS NOT NULL;

ALTER TABLE analysis_login_run
  DROP CONSTRAINT analysis_login_run_pkey;
ALTER TABLE analysis_login_run
  ADD CONSTRAINT analysis_login_run_pkey PRIMARY KEY (login_id);

ALTER TABLE product_store
  ALTER COLUMN sales_7d DROP NOT NULL,
  ALTER COLUMN sales_7d DROP DEFAULT,
  ALTER COLUMN sales_30d DROP NOT NULL,
  ALTER COLUMN sales_30d DROP DEFAULT;

INSERT INTO flow_type_catalog (code, description) VALUES
  ('HIGH', 'Stockout or replenishment risk'),
  ('MEDIUM', 'Stock is adequate for current demand'),
  ('LOW', 'Expiration or excess stock risk')
ON CONFLICT (code) DO NOTHING;

CREATE VIEW suggestion_context AS
SELECT
  s.id,
  s.product_analysis_id,
  s.product_store_id,
  COALESCE(pa.product_id, ps.product_id) AS product_id,
  COALESCE(pa.store_id, ps.store_id) AS store_id,
  s.type,
  s.origin,
  s.status,
  s.ml_batch_count,
  s.current_batch_count,
  s.ml_discount_percentage,
  s.current_discount_percentage,
  s.promotion_valid_from,
  s.promotion_valid_until,
  s.reference_sale_price,
  s.promotional_price,
  s.available_for_triage,
  s.created_by_id,
  s.created_at,
  s.updated_at,
  s.desired_order_date
FROM suggestion s
LEFT JOIN product_analysis pa ON pa.id = s.product_analysis_id
LEFT JOIN product_store ps ON ps.id = s.product_store_id;

CREATE VIEW inventory_movement_context AS
SELECT
  im.id,
  im.erp_id,
  COALESCE(im.batch_id, si.batch_id) AS batch_id,
  b.product_id,
  b.store_id,
  im.movement_type,
  im.quantity,
  im.balance_after,
  im.movement_date,
  im.sale_item_id,
  im.last_sync_id
FROM inventory_movement im
LEFT JOIN sale_item si ON si.id = im.sale_item_id
JOIN batch b ON b.id = COALESCE(im.batch_id, si.batch_id);
