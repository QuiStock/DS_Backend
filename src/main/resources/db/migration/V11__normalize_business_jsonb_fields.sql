-- V11: normalize business JSONB fields while keeping every stored fact.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM public.product_analysis
    WHERE context IS NOT NULL AND jsonb_typeof(context) IS DISTINCT FROM 'object')
    THEN RAISE EXCEPTION 'V11 aborted: product_analysis.context is not an object.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis
    WHERE context IS NOT NULL AND (context - ARRAY[
      'dataset','seed','risks','legacy_metrics','classifier','analysis_status','reason'
    ]::text[]) <> '{}'::jsonb)
    THEN RAISE EXCEPTION 'V11 aborted: unrecognized product_analysis.context keys.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis WHERE context ? 'dataset'
    AND jsonb_typeof(context->'dataset') IS DISTINCT FROM 'string')
    THEN RAISE EXCEPTION 'V11 aborted: context.dataset is not text.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis WHERE context ? 'seed'
    AND (jsonb_typeof(context->'seed') IS DISTINCT FROM 'number'
      OR (context->>'seed') !~ '^-?[0-9]+$'))
    THEN RAISE EXCEPTION 'V11 aborted: context.seed is not an integer.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis WHERE context ? 'risks'
    AND jsonb_typeof(context->'risks') NOT IN ('array','null'))
    THEN RAISE EXCEPTION 'V11 aborted: context.risks is not an array or null.'; END IF;
  IF EXISTS (
    SELECT 1 FROM public.product_analysis pa
    CROSS JOIN LATERAL jsonb_array_elements(
      CASE WHEN jsonb_typeof(pa.context->'risks')='array'
        THEN pa.context->'risks' ELSE '[]'::jsonb END
    ) r(value) WHERE jsonb_typeof(r.value) IS DISTINCT FROM 'string'
  ) THEN RAISE EXCEPTION 'V11 aborted: context.risks has a non-text item.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis
    WHERE context ? 'legacy_metrics'
      AND (jsonb_typeof(context->'legacy_metrics') IS DISTINCT FROM 'object'
        OR ((context->'legacy_metrics') - ARRAY['supplier_lead_time']::text[]) <> '{}'::jsonb))
    THEN RAISE EXCEPTION 'V11 aborted: unrecognized legacy_metrics content.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis
    WHERE context #> '{legacy_metrics,supplier_lead_time}' IS NOT NULL
      AND jsonb_typeof(context #> '{legacy_metrics,supplier_lead_time}') <> 'null'
      AND (jsonb_typeof(context #> '{legacy_metrics,supplier_lead_time}') NOT IN ('number','string')
        OR (context #>> '{legacy_metrics,supplier_lead_time}') !~ '^-?[0-9]+$'))
    THEN RAISE EXCEPTION 'V11 aborted: legacy supplier lead time is not an integer.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis WHERE
    (context ? 'classifier' AND jsonb_typeof(context->'classifier') IS DISTINCT FROM 'string')
    OR (context ? 'analysis_status' AND jsonb_typeof(context->'analysis_status') IS DISTINCT FROM 'string')
    OR (context ? 'reason' AND jsonb_typeof(context->'reason') IS DISTINCT FROM 'string'))
    THEN RAISE EXCEPTION 'V11 aborted: duplicated analysis attributes are not text.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis pa
    WHERE pa.context #> '{legacy_metrics,supplier_lead_time}' IS NOT NULL
      AND jsonb_typeof(pa.context #> '{legacy_metrics,supplier_lead_time}') <> 'null'
      AND pa.supplier_lead_time_days IS NOT NULL
      AND pa.supplier_lead_time_days <>
        (pa.context #>> '{legacy_metrics,supplier_lead_time}')::integer)
    THEN RAISE EXCEPTION 'V11 aborted: typed and legacy supplier lead times conflict.'; END IF;
  IF EXISTS (SELECT 1 FROM public.product_analysis pa WHERE pa.context ? 'classifier'
      AND pa.classifier IS NOT NULL AND pa.classifier IS DISTINCT FROM pa.context->>'classifier')
    OR EXISTS (SELECT 1 FROM public.product_analysis pa WHERE pa.context ? 'analysis_status'
      AND pa.analysis_status IS NOT NULL
      AND pa.analysis_status IS DISTINCT FROM pa.context->>'analysis_status')
    OR EXISTS (SELECT 1 FROM public.product_analysis pa WHERE pa.context ? 'reason'
      AND pa.reason IS NOT NULL AND pa.reason IS DISTINCT FROM pa.context->>'reason')
    THEN RAISE EXCEPTION 'V11 aborted: duplicated analysis attributes conflict.'; END IF;

  IF EXISTS (
    SELECT 1 FROM public.suggestion_log sl
    CROSS JOIN LATERAL (VALUES (sl.previous_data),(sl.new_data)) p(value)
    WHERE p.value IS NOT NULL AND jsonb_typeof(p.value) IS DISTINCT FROM 'object'
  ) THEN RAISE EXCEPTION 'V11 aborted: suggestion_log snapshot is not an object.'; END IF;
  IF EXISTS (
    SELECT 1 FROM public.suggestion_log sl
    CROSS JOIN LATERAL (VALUES (sl.previous_data),(sl.new_data)) p(value)
    WHERE p.value IS NOT NULL AND (p.value - ARRAY[
      'status','type','flow_id','promotion_valid_from','promotion_valid_until',
      'quantity','desired_order_date','discount_percentage','reference_sale_price',
      'promotional_price','source','seed','seed_code'
    ]::text[]) <> '{}'::jsonb
  ) THEN RAISE EXCEPTION 'V11 aborted: unrecognized suggestion_log snapshot keys.'; END IF;
  IF EXISTS (SELECT 1 FROM public.suggestion_log WHERE
    (previous_data ? 'seed' AND (jsonb_typeof(previous_data->'seed') IS DISTINCT FROM 'number'
      OR (previous_data->>'seed') !~ '^-?[0-9]+$'))
    OR (new_data ? 'seed' AND (jsonb_typeof(new_data->'seed') IS DISTINCT FROM 'number'
      OR (new_data->>'seed') !~ '^-?[0-9]+$'))
    OR (previous_data ? 'flow_id' AND (jsonb_typeof(previous_data->'flow_id') IS DISTINCT FROM 'number'
      OR (previous_data->>'flow_id') !~ '^[0-9]+$'))
    OR (new_data ? 'flow_id' AND (jsonb_typeof(new_data->'flow_id') IS DISTINCT FROM 'number'
      OR (new_data->>'flow_id') !~ '^[0-9]+$'))
    OR (previous_data ? 'quantity' AND (jsonb_typeof(previous_data->'quantity') IS DISTINCT FROM 'number'
      OR (previous_data->>'quantity') !~ '^-?[0-9]+$'))
    OR (new_data ? 'quantity' AND (jsonb_typeof(new_data->'quantity') IS DISTINCT FROM 'number'
      OR (new_data->>'quantity') !~ '^-?[0-9]+$'))
  ) THEN RAISE EXCEPTION 'V11 aborted: suggestion_log numeric data is invalid.'; END IF;
  IF EXISTS (SELECT 1 FROM public.suggestion_log sl WHERE
    (sl.new_data ? 'status' AND sl.recorded_status IS NOT NULL
      AND sl.new_data->>'status' IS DISTINCT FROM sl.recorded_status)
    OR (sl.new_data ? 'quantity' AND sl.batch_quantity IS NOT NULL
      AND (sl.new_data->>'quantity')::integer IS DISTINCT FROM sl.batch_quantity)
    OR (sl.new_data ? 'discount_percentage' AND sl.discount_percentage IS NOT NULL
      AND (sl.new_data->>'discount_percentage')::numeric IS DISTINCT FROM sl.discount_percentage)
    OR (sl.new_data ? 'promotional_price' AND sl.promotional_price IS NOT NULL
      AND (sl.new_data->>'promotional_price')::numeric IS DISTINCT FROM sl.promotional_price)
    OR (sl.new_data ? 'promotion_valid_from' AND sl.valid_from IS NOT NULL
      AND (sl.new_data->>'promotion_valid_from')::date IS DISTINCT FROM sl.valid_from)
    OR (sl.new_data ? 'promotion_valid_until' AND sl.valid_until IS NOT NULL
      AND (sl.new_data->>'promotion_valid_until')::date IS DISTINCT FROM sl.valid_until)
  ) THEN RAISE EXCEPTION 'V11 aborted: JSON and typed suggestion_log snapshots conflict.'; END IF;
  IF EXISTS (SELECT 1 FROM public.suggestion_log WHERE previous_data ? 'source'
    AND new_data ? 'source' AND previous_data->>'source' IS DISTINCT FROM new_data->>'source')
    OR EXISTS (SELECT 1 FROM public.suggestion_log WHERE previous_data ? 'seed'
    AND new_data ? 'seed' AND previous_data->>'seed' IS DISTINCT FROM new_data->>'seed')
    OR EXISTS (SELECT 1 FROM public.suggestion_log WHERE previous_data ? 'seed_code'
    AND new_data ? 'seed_code' AND previous_data->>'seed_code' IS DISTINCT FROM new_data->>'seed_code')
    THEN RAISE EXCEPTION 'V11 aborted: suggestion_log provenance conflicts.'; END IF;

  IF EXISTS (SELECT 1 FROM public.audit_event
    WHERE data IS NULL OR jsonb_typeof(data) IS DISTINCT FROM 'object')
    THEN RAISE EXCEPTION 'V11 aborted: audit_event.data is missing or not an object.'; END IF;
  IF EXISTS (SELECT 1 FROM public.audit_event
    WHERE entity NOT IN ('suggestion','suggestion_decision','user_account'))
    THEN RAISE EXCEPTION 'V11 aborted: audit_event has an unrecognized entity.'; END IF;
  IF EXISTS (SELECT 1 FROM public.audit_event a WHERE a.entity IN ('suggestion','suggestion_decision')
    AND ((a.data - ARRAY['NEW','OLD','TG_OP','CURRENT_USER']::text[]) <> '{}'::jsonb
      OR a.data->>'TG_OP' IS DISTINCT FROM a.action
      OR NULLIF(a.data->>'CURRENT_USER','') IS NULL
      OR a.action NOT IN ('INSERT','UPDATE','DELETE')
      OR (a.action IN ('INSERT','UPDATE') AND jsonb_typeof(a.data->'NEW') IS DISTINCT FROM 'object')
      OR (a.action IN ('UPDATE','DELETE') AND jsonb_typeof(a.data->'OLD') IS DISTINCT FROM 'object')
      OR (a.action='INSERT' AND jsonb_typeof(a.data->'OLD') IS DISTINCT FROM 'null')
      OR (a.action='DELETE' AND jsonb_typeof(a.data->'NEW') IS DISTINCT FROM 'null')
      OR (a.action IN ('INSERT','UPDATE')
        AND NULLIF(a.data->'NEW'->>'id','')::bigint IS DISTINCT FROM a.entity_id)
      OR (a.action IN ('UPDATE','DELETE')
        AND NULLIF(a.data->'OLD'->>'id','')::bigint IS DISTINCT FROM a.entity_id)))
    THEN RAISE EXCEPTION 'V11 aborted: audit operation metadata is inconsistent.'; END IF;
  IF EXISTS (
    SELECT 1 FROM public.audit_event a
    CROSS JOIN LATERAL (VALUES (a.data->'NEW'),(a.data->'OLD')) image(value)
    WHERE a.entity='suggestion' AND jsonb_typeof(image.value)='object'
      AND ((CASE WHEN jsonb_typeof(image.value)='object' THEN image.value ELSE '{}'::jsonb END) - ARRAY[
        'id','product_analysis_id','product_store_id','type','origin','status','ml_batch_count',
        'current_batch_count','ml_discount_percentage','current_discount_percentage',
        'promotion_valid_from','promotion_valid_until','reference_sale_price','promotional_price',
        'available_for_triage','created_by_id','created_at','updated_at','desired_order_date'
      ]::text[]) <> '{}'::jsonb
  ) OR EXISTS (
    SELECT 1 FROM public.audit_event a
    CROSS JOIN LATERAL (VALUES (a.data->'NEW'),(a.data->'OLD')) image(value)
    WHERE a.entity='suggestion_decision' AND jsonb_typeof(image.value)='object'
      AND ((CASE WHEN jsonb_typeof(image.value)='object' THEN image.value ELSE '{}'::jsonb END) - ARRAY[
        'id','suggestion_id','manager_id','decision','final_batch_count','final_discount_percentage',
        'final_promotional_price','final_promotion_valid_from','final_promotion_valid_until',
        'justification','decided_at'
      ]::text[]) <> '{}'::jsonb
  ) THEN RAISE EXCEPTION 'V11 aborted: audit snapshot has unknown fields.'; END IF;
  IF EXISTS (SELECT 1 FROM public.audit_event a WHERE a.entity='user_account'
    AND (a.data - ARRAY['role','email','CURRENT_USER']::text[]) <> '{}'::jsonb)
    THEN RAISE EXCEPTION 'V11 aborted: user_account audit has unknown fields.'; END IF;
END;
$$;

ALTER TABLE public.product_analysis
  ADD COLUMN source_context_present BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN source_dataset TEXT,
  ADD COLUMN source_seed BIGINT,
  ADD COLUMN source_classifier_present BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN source_analysis_status_present BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN source_reason_present BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN risks_present BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN risks_is_null BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN legacy_metrics_present BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN legacy_supplier_lead_time_present BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE public.product_analysis_risk (
  product_analysis_id BIGINT NOT NULL REFERENCES public.product_analysis(id) ON DELETE CASCADE,
  risk_position BIGINT NOT NULL CHECK (risk_position>0),
  risk_code TEXT NOT NULL,
  PRIMARY KEY (product_analysis_id,risk_position)
);

UPDATE public.product_analysis pa
SET source_context_present=pa.context IS NOT NULL,
    source_dataset=pa.context->>'dataset',
    source_seed=CASE WHEN pa.context ? 'seed' THEN (pa.context->>'seed')::bigint END,
    source_classifier_present=COALESCE(pa.context ? 'classifier',FALSE),
    source_analysis_status_present=COALESCE(pa.context ? 'analysis_status',FALSE),
    source_reason_present=COALESCE(pa.context ? 'reason',FALSE),
    risks_present=COALESCE(pa.context ? 'risks',FALSE),
    risks_is_null=COALESCE(jsonb_typeof(pa.context->'risks')='null',FALSE),
    legacy_metrics_present=COALESCE(pa.context ? 'legacy_metrics',FALSE),
    legacy_supplier_lead_time_present=COALESCE(
      (pa.context->'legacy_metrics') ? 'supplier_lead_time',FALSE),
    classifier=COALESCE(pa.classifier,pa.context->>'classifier'),
    analysis_status=COALESCE(pa.analysis_status,pa.context->>'analysis_status'),
    reason=COALESCE(pa.reason,pa.context->>'reason'),
    supplier_lead_time_days=COALESCE(pa.supplier_lead_time_days,
      NULLIF(pa.context #>> '{legacy_metrics,supplier_lead_time}','null')::integer);

ALTER TABLE public.product_analysis
  ADD CONSTRAINT chk_product_analysis_risks_null
  CHECK (NOT risks_is_null OR risks_present);

INSERT INTO public.product_analysis_risk(product_analysis_id,risk_position,risk_code)
SELECT pa.id,r.position,r.value #>> '{}'
FROM public.product_analysis pa
CROSS JOIN LATERAL jsonb_array_elements(
  CASE WHEN jsonb_typeof(pa.context->'risks')='array' THEN pa.context->'risks'
    ELSE '[]'::jsonb END
) WITH ORDINALITY r(value,position);

ALTER TABLE public.suggestion_log ADD COLUMN source TEXT, ADD COLUMN seed BIGINT, ADD COLUMN seed_code TEXT;

CREATE TABLE public.suggestion_log_snapshot (
  suggestion_log_id BIGINT NOT NULL REFERENCES public.suggestion_log(id) ON DELETE CASCADE,
  snapshot_side VARCHAR(6) NOT NULL CHECK (snapshot_side IN ('BEFORE','AFTER')),
  status TEXT,
  type public.suggestion_type,
  flow_id BIGINT,
  quantity INTEGER,
  desired_order_date DATE,
  discount_percentage NUMERIC(5,2),
  reference_sale_price NUMERIC(12,2),
  promotional_price NUMERIC(12,2),
  promotion_valid_from DATE,
  promotion_valid_until DATE,
  PRIMARY KEY (suggestion_log_id,snapshot_side),
  CHECK ((promotion_valid_from IS NULL AND promotion_valid_until IS NULL)
    OR (promotion_valid_from IS NOT NULL AND promotion_valid_until IS NOT NULL
      AND promotion_valid_until>=promotion_valid_from))
);

UPDATE public.suggestion_log sl
SET source=COALESCE(sl.new_data->>'source',sl.previous_data->>'source'),
    seed=COALESCE((sl.new_data->>'seed')::bigint,(sl.previous_data->>'seed')::bigint),
    seed_code=COALESCE(sl.new_data->>'seed_code',sl.previous_data->>'seed_code');

INSERT INTO public.suggestion_log_snapshot (
  suggestion_log_id,snapshot_side,status,type,flow_id,quantity,desired_order_date,
  discount_percentage,reference_sale_price,promotional_price,promotion_valid_from,promotion_valid_until
)
SELECT sl.id,'BEFORE',sl.previous_data->>'status',
       NULLIF(sl.previous_data->>'type','')::public.suggestion_type,
       NULLIF(sl.previous_data->>'flow_id','')::bigint,
       NULLIF(sl.previous_data->>'quantity','')::integer,
       NULLIF(sl.previous_data->>'desired_order_date','')::date,
       NULLIF(sl.previous_data->>'discount_percentage','')::numeric(5,2),
       NULLIF(sl.previous_data->>'reference_sale_price','')::numeric(12,2),
       NULLIF(sl.previous_data->>'promotional_price','')::numeric(12,2),
       NULLIF(sl.previous_data->>'promotion_valid_from','')::date,
       NULLIF(sl.previous_data->>'promotion_valid_until','')::date
FROM public.suggestion_log sl WHERE sl.previous_data IS NOT NULL;

INSERT INTO public.suggestion_log_snapshot (
  suggestion_log_id,snapshot_side,status,type,flow_id,quantity,desired_order_date,
  discount_percentage,reference_sale_price,promotional_price,promotion_valid_from,promotion_valid_until
)
SELECT sl.id,'AFTER',
       COALESCE(sl.new_data->>'status',sl.recorded_status),
       COALESCE(NULLIF(sl.new_data->>'type','')::public.suggestion_type,sg.type),
       COALESCE(NULLIF(sl.new_data->>'flow_id','')::bigint,sg.product_analysis_id),
       COALESCE(NULLIF(sl.new_data->>'quantity','')::integer,sl.batch_quantity,sg.current_batch_count),
       COALESCE(NULLIF(sl.new_data->>'desired_order_date','')::date,sg.desired_order_date),
       COALESCE(NULLIF(sl.new_data->>'discount_percentage','')::numeric(5,2),
         sl.discount_percentage,sg.current_discount_percentage),
       COALESCE(NULLIF(sl.new_data->>'reference_sale_price','')::numeric(12,2),sg.reference_sale_price),
       COALESCE(NULLIF(sl.new_data->>'promotional_price','')::numeric(12,2),
         sl.promotional_price,sg.promotional_price),
       COALESCE(NULLIF(sl.new_data->>'promotion_valid_from','')::date,sl.valid_from,sg.promotion_valid_from),
       COALESCE(NULLIF(sl.new_data->>'promotion_valid_until','')::date,sl.valid_until,sg.promotion_valid_until)
FROM public.suggestion_log sl JOIN public.suggestion sg ON sg.id=sl.suggestion_id;

ALTER TABLE public.audit_event ADD COLUMN database_user TEXT;
UPDATE public.audit_event SET database_user=data->>'CURRENT_USER' WHERE data ? 'CURRENT_USER';

CREATE TABLE public.audit_event_suggestion_snapshot (
  audit_event_id BIGINT NOT NULL REFERENCES public.audit_event(id) ON DELETE CASCADE,
  image_side VARCHAR(3) NOT NULL CHECK (image_side IN ('NEW','OLD')),
  product_analysis_id BIGINT,
  product_store_id BIGINT,
  type public.suggestion_type NOT NULL,
  origin public.suggestion_origin NOT NULL,
  status public.suggestion_status NOT NULL,
  ml_batch_count INTEGER,
  current_batch_count INTEGER,
  ml_discount_percentage NUMERIC(5,2),
  current_discount_percentage NUMERIC(5,2),
  promotion_valid_from DATE,
  promotion_valid_until DATE,
  reference_sale_price NUMERIC(12,2),
  promotional_price NUMERIC(12,2),
  available_for_triage BOOLEAN NOT NULL,
  created_by_id BIGINT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ,
  desired_order_date DATE,
  PRIMARY KEY (audit_event_id,image_side)
);

CREATE TABLE public.audit_event_decision_snapshot (
  audit_event_id BIGINT NOT NULL REFERENCES public.audit_event(id) ON DELETE CASCADE,
  image_side VARCHAR(3) NOT NULL CHECK (image_side IN ('NEW','OLD')),
  suggestion_id BIGINT NOT NULL,
  manager_id BIGINT NOT NULL,
  decision public.decision_type NOT NULL,
  final_batch_count INTEGER,
  final_discount_percentage NUMERIC(5,2),
  final_promotional_price NUMERIC(12,2),
  final_promotion_valid_from DATE,
  final_promotion_valid_until DATE,
  justification TEXT,
  decided_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (audit_event_id,image_side)
);

CREATE TABLE public.audit_event_user_account_detail (
  audit_event_id BIGINT PRIMARY KEY REFERENCES public.audit_event(id) ON DELETE CASCADE,
  role_code TEXT,
  email TEXT
);

INSERT INTO public.audit_event_suggestion_snapshot (
  audit_event_id,image_side,product_analysis_id,product_store_id,type,origin,status,
  ml_batch_count,current_batch_count,ml_discount_percentage,current_discount_percentage,
  promotion_valid_from,promotion_valid_until,reference_sale_price,promotional_price,
  available_for_triage,created_by_id,created_at,updated_at,desired_order_date
)
SELECT a.id,image.side,
       NULLIF(image.value->>'product_analysis_id','')::bigint,
       NULLIF(image.value->>'product_store_id','')::bigint,
       (image.value->>'type')::public.suggestion_type,
       (image.value->>'origin')::public.suggestion_origin,
       (image.value->>'status')::public.suggestion_status,
       NULLIF(image.value->>'ml_batch_count','')::integer,
       NULLIF(image.value->>'current_batch_count','')::integer,
       NULLIF(image.value->>'ml_discount_percentage','')::numeric(5,2),
       NULLIF(image.value->>'current_discount_percentage','')::numeric(5,2),
       NULLIF(image.value->>'promotion_valid_from','')::date,
       NULLIF(image.value->>'promotion_valid_until','')::date,
       NULLIF(image.value->>'reference_sale_price','')::numeric(12,2),
       NULLIF(image.value->>'promotional_price','')::numeric(12,2),
       (image.value->>'available_for_triage')::boolean,
       NULLIF(image.value->>'created_by_id','')::bigint,
       (image.value->>'created_at')::timestamptz,
       NULLIF(image.value->>'updated_at','')::timestamptz,
       NULLIF(image.value->>'desired_order_date','')::date
FROM public.audit_event a
CROSS JOIN LATERAL (VALUES ('NEW'::varchar,a.data->'NEW'),('OLD'::varchar,a.data->'OLD'))
  image(side,value)
WHERE a.entity='suggestion' AND jsonb_typeof(image.value)='object';

INSERT INTO public.audit_event_decision_snapshot (
  audit_event_id,image_side,suggestion_id,manager_id,decision,final_batch_count,
  final_discount_percentage,final_promotional_price,final_promotion_valid_from,
  final_promotion_valid_until,justification,decided_at
)
SELECT a.id,image.side,(image.value->>'suggestion_id')::bigint,
       (image.value->>'manager_id')::bigint,(image.value->>'decision')::public.decision_type,
       NULLIF(image.value->>'final_batch_count','')::integer,
       NULLIF(image.value->>'final_discount_percentage','')::numeric(5,2),
       NULLIF(image.value->>'final_promotional_price','')::numeric(12,2),
       NULLIF(image.value->>'final_promotion_valid_from','')::date,
       NULLIF(image.value->>'final_promotion_valid_until','')::date,
       image.value->>'justification',(image.value->>'decided_at')::timestamptz
FROM public.audit_event a
CROSS JOIN LATERAL (VALUES ('NEW'::varchar,a.data->'NEW'),('OLD'::varchar,a.data->'OLD'))
  image(side,value)
WHERE a.entity='suggestion_decision' AND jsonb_typeof(image.value)='object';

INSERT INTO public.audit_event_user_account_detail(audit_event_id,role_code,email)
SELECT a.id,a.data->>'role',a.data->>'email'
FROM public.audit_event a
WHERE a.entity='user_account' AND (a.data ? 'role' OR a.data ? 'email');

CREATE OR REPLACE FUNCTION public.capture_audit_event()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
  v_entity_id BIGINT;
  v_user_id BIGINT;
  v_event_id BIGINT;
  v_new JSONB;
  v_old JSONB;
BEGIN
  IF TG_OP='DELETE' THEN
    v_entity_id:=OLD.id; v_old:=to_jsonb(OLD);
  ELSE
    v_entity_id:=NEW.id; v_new:=to_jsonb(NEW);
  END IF;
  IF TG_OP='UPDATE' THEN v_old:=to_jsonb(OLD); END IF;

  IF TG_TABLE_NAME='suggestion' THEN
    v_user_id:=CASE WHEN TG_OP='DELETE' THEN OLD.created_by_id ELSE NEW.created_by_id END;
  ELSIF TG_TABLE_NAME='suggestion_decision' THEN
    v_user_id:=CASE WHEN TG_OP='DELETE' THEN OLD.manager_id ELSE NEW.manager_id END;
  END IF;

  INSERT INTO public.audit_event(user_id,entity,entity_id,action,database_user,occurred_at)
  VALUES(v_user_id,TG_TABLE_NAME,v_entity_id,TG_OP,CURRENT_USER,CURRENT_TIMESTAMP)
  RETURNING id INTO v_event_id;

  IF TG_TABLE_NAME='suggestion' THEN
    IF v_new IS NOT NULL THEN
      INSERT INTO public.audit_event_suggestion_snapshot (
        audit_event_id,image_side,product_analysis_id,product_store_id,type,origin,status,
        ml_batch_count,current_batch_count,ml_discount_percentage,current_discount_percentage,
        promotion_valid_from,promotion_valid_until,reference_sale_price,promotional_price,
        available_for_triage,created_by_id,created_at,updated_at,desired_order_date
      ) VALUES (
        v_event_id,'NEW',NULLIF(v_new->>'product_analysis_id','')::bigint,
        NULLIF(v_new->>'product_store_id','')::bigint,(v_new->>'type')::public.suggestion_type,
        (v_new->>'origin')::public.suggestion_origin,(v_new->>'status')::public.suggestion_status,
        NULLIF(v_new->>'ml_batch_count','')::integer,NULLIF(v_new->>'current_batch_count','')::integer,
        NULLIF(v_new->>'ml_discount_percentage','')::numeric(5,2),
        NULLIF(v_new->>'current_discount_percentage','')::numeric(5,2),
        NULLIF(v_new->>'promotion_valid_from','')::date,NULLIF(v_new->>'promotion_valid_until','')::date,
        NULLIF(v_new->>'reference_sale_price','')::numeric(12,2),
        NULLIF(v_new->>'promotional_price','')::numeric(12,2),
        (v_new->>'available_for_triage')::boolean,NULLIF(v_new->>'created_by_id','')::bigint,
        (v_new->>'created_at')::timestamptz,NULLIF(v_new->>'updated_at','')::timestamptz,
        NULLIF(v_new->>'desired_order_date','')::date
      );
    END IF;
    IF v_old IS NOT NULL THEN
      INSERT INTO public.audit_event_suggestion_snapshot (
        audit_event_id,image_side,product_analysis_id,product_store_id,type,origin,status,
        ml_batch_count,current_batch_count,ml_discount_percentage,current_discount_percentage,
        promotion_valid_from,promotion_valid_until,reference_sale_price,promotional_price,
        available_for_triage,created_by_id,created_at,updated_at,desired_order_date
      ) VALUES (
        v_event_id,'OLD',NULLIF(v_old->>'product_analysis_id','')::bigint,
        NULLIF(v_old->>'product_store_id','')::bigint,(v_old->>'type')::public.suggestion_type,
        (v_old->>'origin')::public.suggestion_origin,(v_old->>'status')::public.suggestion_status,
        NULLIF(v_old->>'ml_batch_count','')::integer,NULLIF(v_old->>'current_batch_count','')::integer,
        NULLIF(v_old->>'ml_discount_percentage','')::numeric(5,2),
        NULLIF(v_old->>'current_discount_percentage','')::numeric(5,2),
        NULLIF(v_old->>'promotion_valid_from','')::date,NULLIF(v_old->>'promotion_valid_until','')::date,
        NULLIF(v_old->>'reference_sale_price','')::numeric(12,2),
        NULLIF(v_old->>'promotional_price','')::numeric(12,2),
        (v_old->>'available_for_triage')::boolean,NULLIF(v_old->>'created_by_id','')::bigint,
        (v_old->>'created_at')::timestamptz,NULLIF(v_old->>'updated_at','')::timestamptz,
        NULLIF(v_old->>'desired_order_date','')::date
      );
    END IF;
  ELSIF TG_TABLE_NAME='suggestion_decision' THEN
    IF v_new IS NOT NULL THEN
      INSERT INTO public.audit_event_decision_snapshot (
        audit_event_id,image_side,suggestion_id,manager_id,decision,final_batch_count,
        final_discount_percentage,final_promotional_price,final_promotion_valid_from,
        final_promotion_valid_until,justification,decided_at
      ) VALUES (
        v_event_id,'NEW',(v_new->>'suggestion_id')::bigint,(v_new->>'manager_id')::bigint,
        (v_new->>'decision')::public.decision_type,NULLIF(v_new->>'final_batch_count','')::integer,
        NULLIF(v_new->>'final_discount_percentage','')::numeric(5,2),
        NULLIF(v_new->>'final_promotional_price','')::numeric(12,2),
        NULLIF(v_new->>'final_promotion_valid_from','')::date,
        NULLIF(v_new->>'final_promotion_valid_until','')::date,
        v_new->>'justification',(v_new->>'decided_at')::timestamptz
      );
    END IF;
    IF v_old IS NOT NULL THEN
      INSERT INTO public.audit_event_decision_snapshot (
        audit_event_id,image_side,suggestion_id,manager_id,decision,final_batch_count,
        final_discount_percentage,final_promotional_price,final_promotion_valid_from,
        final_promotion_valid_until,justification,decided_at
      ) VALUES (
        v_event_id,'OLD',(v_old->>'suggestion_id')::bigint,(v_old->>'manager_id')::bigint,
        (v_old->>'decision')::public.decision_type,NULLIF(v_old->>'final_batch_count','')::integer,
        NULLIF(v_old->>'final_discount_percentage','')::numeric(5,2),
        NULLIF(v_old->>'final_promotional_price','')::numeric(12,2),
        NULLIF(v_old->>'final_promotion_valid_from','')::date,
        NULLIF(v_old->>'final_promotion_valid_until','')::date,
        v_old->>'justification',(v_old->>'decided_at')::timestamptz
      );
    END IF;
  END IF;

  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END;
$$;

ALTER TABLE public.product_analysis DROP COLUMN context;
ALTER TABLE public.suggestion_log
  DROP COLUMN previous_data,
  DROP COLUMN new_data,
  DROP COLUMN recorded_status,
  DROP COLUMN batch_quantity,
  DROP COLUMN discount_percentage,
  DROP COLUMN promotional_price,
  DROP COLUMN valid_from,
  DROP COLUMN valid_until;
ALTER TABLE public.audit_event DROP COLUMN data;

COMMENT ON TABLE public.product_analysis_risk IS
  'Riscos ordenados de uma análise; substitui product_analysis.context.risks.';
COMMENT ON COLUMN public.product_analysis.source_dataset IS
  'Origem da carga; substitui product_analysis.context.dataset.';
COMMENT ON COLUMN public.product_analysis.source_seed IS
  'Seed de reprodutibilidade; substitui product_analysis.context.seed.';
COMMENT ON COLUMN public.product_analysis.risks_is_null IS
  'Preserva o null original de context.risks; distingue null de lista vazia.';
COMMENT ON TABLE public.suggestion_log_snapshot IS
  'Snapshots tipados BEFORE/AFTER de uma ocorrência de sugestão.';
COMMENT ON COLUMN public.audit_event.database_user IS
  'CURRENT_USER no instante da operação auditada.';
COMMENT ON TABLE public.audit_event_suggestion_snapshot IS
  'Imagens OLD/NEW tipadas de suggestion.';
COMMENT ON TABLE public.audit_event_decision_snapshot IS
  'Imagens OLD/NEW tipadas de suggestion_decision.';
COMMENT ON TABLE public.audit_event_user_account_detail IS
  'Campos específicos de auditoria de user_account, separados do evento.';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='quistock_core_app') THEN
    EXECUTE 'GRANT SELECT, INSERT ON TABLE public.product_analysis_risk, public.suggestion_log_snapshot TO quistock_core_app';
    EXECUTE 'GRANT INSERT ON TABLE public.audit_event_suggestion_snapshot, public.audit_event_decision_snapshot, public.audit_event_user_account_detail TO quistock_core_app';
    EXECUTE 'GRANT SELECT (id) ON TABLE public.audit_event TO quistock_core_app';
  END IF;
END;
$$;

