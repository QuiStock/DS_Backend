DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM product
    WHERE sale_price IS NULL OR char_length(sku) > 100
  ) THEN
    RAISE EXCEPTION
      'Cannot enforce product ERP requirements: sale_price is null or sku exceeds 100 characters.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM batch
    WHERE expiration_date IS NULL OR sale_price IS NULL OR unit_cost IS NULL
  ) THEN
    RAISE EXCEPTION
      'Cannot enforce batch ERP requirements: expiration_date, sale_price, or unit_cost is null.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM product_store
    WHERE sale_price IS NULL OR unit_cost IS NULL
  ) THEN
    RAISE EXCEPTION
      'Cannot enforce product_store ERP requirements: sale_price or unit_cost is null.';
  END IF;
END;
$$;

ALTER TABLE "product"
  ALTER COLUMN "sku" TYPE VARCHAR(100),
  ALTER COLUMN "sale_price" SET NOT NULL;

ALTER TABLE "batch"
  ALTER COLUMN "expiration_date" SET NOT NULL,
  ALTER COLUMN "sale_price" SET NOT NULL,
  ALTER COLUMN "unit_cost" SET NOT NULL;

ALTER TABLE "product_store"
  ALTER COLUMN "sale_price" SET NOT NULL,
  ALTER COLUMN "unit_cost" SET NOT NULL;

ALTER TABLE "suggestion"
  DROP CONSTRAINT "chk_suggestion_type_fields";

ALTER TABLE "suggestion"
  ADD CONSTRAINT "chk_suggestion_type_fields"
  CHECK (
    (
      type = 'ORDER'
      AND ml_discount_percentage IS NULL
      AND current_discount_percentage IS NULL
      AND promotion_valid_from IS NULL
      AND promotion_valid_until IS NULL
      AND reference_sale_price IS NULL
      AND promotional_price IS NULL
    )
    OR (
      type = 'PROMOTION'
      AND promotion_valid_from IS NOT NULL
      AND promotion_valid_until IS NOT NULL
      AND promotion_valid_until >= promotion_valid_from
    )
    OR (
      type = 'MONITOR'
      AND product_analysis_id IS NOT NULL
      AND origin = 'ML'
      AND status = 'GENERATED'
      AND ml_batch_count IS NULL
      AND current_batch_count IS NULL
      AND ml_discount_percentage IS NULL
      AND current_discount_percentage IS NULL
      AND promotion_valid_from IS NULL
      AND promotion_valid_until IS NULL
      AND reference_sale_price IS NULL
      AND promotional_price IS NULL
      AND available_for_triage = FALSE
    )
  );
