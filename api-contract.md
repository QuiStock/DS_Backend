# QuiStock API Contract

Version: 1.1.0
Last updated: 2026-08-28

## Base URL

Local:

`http://localhost:8080/api`

---

## Data source

The data returned by the API originates from an external ERP API.

For the MVP, the ERP is represented by a MockAPI containing fake data with the same
structure as a real ERP integration.

The external endpoint consumed by the backend is `GET /products`. Each returned record
represents an ERP batch, not a consolidated product. The backend normalizes the records,
groups batches by product and branch, and exposes a stable product model to the frontend.

The external MockAPI currently uses Portuguese field names. Those names are kept only at
the integration boundary; all public API fields and internal code identifiers are English.

---

# 1. Products

## 1.1 List products

Returns products consolidated by the backend from the batches provided by the MockAPI.

Method:

`GET /products`

Response 200:

```json
[
  {
    "id": "PROD001:FIL001",
    "sku": "PROD001",
    "name": "Whole Milk 1L",
    "category": "Dairy",
    "current_stock": 121,
    "minimum_stock": 40,
    "sales_7d": 50,
    "sales_30d": 200,
    "expiration_days": 12,
    "supplier_lead_time": 5,
    "price": 7.99,
    "cost": 5.20,
    "last_restock": "2026-08-20T00:00:00Z",
    "status": true,
    "branch": "Santana Store"
  }
]
```

### Batch consolidation

Each record received from the ERP `GET /products` endpoint represents one batch. The
backend consolidates all records before creating the public response.

The grouping key is:

```text
erp_product_code + erp_branch_code
```

The same product in different branches produces different consolidated products. Grouping
is never based only on the product name or product code.

Transformation rules:

- `sku` receives the ERP `codigo_produto_erp` value.
- `name` receives `nome_produto`.
- `category` receives `categoria`.
- `branch` receives `filial`.
- `current_stock` is the sum of `quantidade` for all batches in the group.
- `sales_7d` is the sum of `vendas_7d` for all batches in the group.
- `sales_30d` is the sum of `vendas_30d` for all batches in the group.
- `expiration_days` is calculated using the nearest expiration date among batches with
  quantity greater than zero.
- `minimum_stock` is not summed. If batches disagree, the highest valid value is used and
  the inconsistency is logged.
- `supplier_lead_time` receives `lead_time_dias` and is not summed. If batches disagree,
  the highest valid value is used and the inconsistency is logged.
- `price` and `cost` come from the most recent batch according to `data_entrada`.
- `status` is `true` when consolidated stock is greater than zero.
- `id` is deterministic in this non-persistent stage:
  `codigo_produto_erp:codigo_filial_erp`.

Internal batch fields such as `num_lote`, `certificado_qualidade`, `data_entrada`,
`data_validade`, and `unidade_medida` are not exposed by this public route.

Dates may be received as ISO-8601 dates, Unix timestamps in seconds, or Unix timestamps in
milliseconds. Numeric values may be JSON numbers or numeric strings. The backend
normalizes these formats before consolidation.

An expired batch may produce a negative `expiration_days` value. The implementation uses
the actual number of days between the current date and the expiration date. If no available
batch has an expiration date, the field is `null`.

### Optional filters

Filters are applied after batch consolidation:

```text
GET /products?branch=Downtown%20Store
GET /products?category=Dairy
GET /products?status=true
```

Available parameters:

- `branch`: filters products by branch.
- `category`: filters products by category.
- `status`: filters active or inactive products.

---

## 1.2 Get a product by ID

Method:

`GET /products/{id}`

Example:

`GET /products/PROD001:FIL001`

Response 200:

```json
{
  "id": "PROD001:FIL001",
  "sku": "PROD001",
  "name": "Whole Milk 1L",
  "category": "Dairy",
  "current_stock": 51,
  "minimum_stock": 58,
  "sales_7d": 36,
  "sales_30d": 150,
  "expiration_days": 0,
  "supplier_lead_time": 3,
  "price": 7.90,
  "cost": 4.50,
  "status": true,
  "branch": "Downtown Store"
}
```

---

# 2. Flows

A flow is the product classification produced by an analysis.

Possible types:

- `HIGH`: the product is at risk of stockout.
- `MEDIUM`: the stock level is adequate.
- `LOW`: the product may remain in excess or expire.

## Classification

In the MVP, a simple backend classifier determines `HIGH`, `MEDIUM`, or `LOW` using:

- `current_stock`
- `sales_7d`
- `sales_30d`
- `expiration_days`
- `supplier_lead_time`

The architecture allows this logic to be replaced by a trained machine-learning model in
the future.

---

## 2.1 Analyze a product

Method:

`POST /flows/analyze`

Body:

```json
{
  "product_id": "PROD001:FIL001"
}
```

Response 201:

```json
{
  "id": "101",
  "product_id": "PROD001:FIL001",
  "product_name": "Whole Milk 1L",
  "flow_type": "LOW",
  "status": "ANALYZED",
  "reason": "Product is expired or close to expiration.",
  "daily_sales_average": 5.14,
  "stock_coverage_days": 9.92,
  "expiration_days": 0,
  "supplier_lead_time": 3,
  "analysis_date": "2026-07-30T11:47:00-03:00"
}
```

---

## 2.2 List analyzed flows

Method:

`GET /flows`

Response 200:

```json
[
  {
    "id": "101",
    "product_id": "PROD001:FIL001",
    "product_name": "Whole Milk 1L",
    "flow_type": "LOW",
    "status": "ANALYZED",
    "reason": "Product is expired or close to expiration."
  }
]
```

### Optional filters

```text
GET /flows?flow_type=LOW
GET /flows?product_id=PROD001:FIL001
GET /flows?status=ANALYZED
```

Available parameters:

- `flow_type`: `HIGH`, `MEDIUM`, or `LOW`.
- `product_id`: filters flows for one product.
- `status`: filters by analysis status.

---

# 3. Actions

An action is the recommendation generated from a flow.

Possible types:

- `PROMOTION`
- `STOCK_ORDER`
- `MONITOR`

Possible statuses:

- `SUGGESTED`
- `APPROVED`
- `REJECTED`
- `COMPLETED`

Generation rules:

- `HIGH` flow generates `STOCK_ORDER`.
- `MEDIUM` flow generates `MONITOR`.
- `LOW` flow generates `PROMOTION`.

---

## 3.1 Generate actions for a flow

Method:

`POST /actions/generate`

Body:

```json
{
  "flow_id": "101"
}
```

Response 201:

```json
{
  "flow_id": "101",
  "generated_actions": [
    {
      "id": "501",
      "action_type": "PROMOTION",
      "status": "SUGGESTED",
      "justification": "Product has expiration or excess stock risk."
    }
  ]
}
```

---

## 3.2 List actions

Method:

`GET /actions`

Response 200:

```json
[
  {
    "id": "501",
    "flow_id": "101",
    "product_name": "Whole Milk 1L",
    "action_type": "PROMOTION",
    "status": "SUGGESTED",
    "justification": "Product has expiration risk."
  }
]
```

### Optional filters

```text
GET /actions?status=SUGGESTED
GET /actions?action_type=PROMOTION
GET /actions?flow_id=101
```

Available parameters:

- `status`: `SUGGESTED`, `APPROVED`, `REJECTED`, or `COMPLETED`.
- `action_type`: `PROMOTION`, `STOCK_ORDER`, or `MONITOR`.
- `flow_id`: filters actions generated from one flow.

---

## 3.3 Update an action status

Method:

`PATCH /actions/{id}/status`

Example:

`PATCH /actions/501/status`

Body:

```json
{
  "status": "APPROVED"
}
```

Response 200:

```json
{
  "id": "501",
  "status": "APPROVED"
}
```

---

# 4. Chatbot

## 4.1 Send a chatbot message

Method:

`POST /chat`

Body:

```json
{
  "user_id": "1",
  "message": "Which products need a promotion?"
}
```

Response 200:

```json
{
  "answer": "There are products at risk of expiration. The main suggestion is to create a promotion for Whole Milk 1L.",
  "responsible_agent": "operations_agent",
  "referenced_data": [
    {
      "product_id": "PROD001:FIL001",
      "name": "Whole Milk 1L",
      "flow_type": "LOW",
      "suggested_action": "PROMOTION"
    }
  ]
}
```

---

# 5. Branches

## 5.1 List branches

Method:

`GET /branches`

Response 200:

```json
[
  {
    "id": "1",
    "name": "Downtown Store",
    "address": "1000 Paulista Avenue",
    "city": "Sao Paulo",
    "state": "SP",
    "latitude": -23.561684,
    "longitude": -46.655981
  }
]
```

---

# 6. Dashboard

Dashboard metrics are calculated from analyzed products, generated flows, and suggested
actions.

## 6.1 Get the dashboard summary

Method:

`GET /dashboard/summary`

Response 200:

```json
{
  "total_products": 5,
  "high_risk_products": 1,
  "medium_risk_products": 2,
  "low_risk_products": 2,
  "suggested_actions": 3,
  "suggested_promotions": 2,
  "suggested_stock_orders": 1,
  "near_expiry_products": 4,
  "stockout_products": 2,
  "overstock_products": 3,
  "active_actions": 5
}
```

The four additional Big Numbers are calculated from the consolidated products and the
actions already managed by the backend. Products are counted by product and branch, not
by individual ERP batch.

- `near_expiry_products`: positive stock and nearest expiration within the configured
  limit, with a default of 30 days.
- `stockout_products`: consolidated stock less than or equal to zero.
- `overstock_products`: consolidated stock greater than minimum stock.
- `active_actions`: actions with `SUGGESTED` or `APPROVED` status.

The expiration threshold can be configured with `dashboard.short-expiry-days` or the
`DASHBOARD_SHORT_EXPIRY_DAYS` environment variable.

---

# 7. ERP integration

## 7.1 Test the ERP connection

Method:

`GET /erp-integration/status`

Response 200:

```json
{
  "source": "MockAPI",
  "status": "CONNECTED",
  "last_synchronization": "2026-07-30T11:47:00-03:00"
}
```

---

# 8. Error format

## Product not found

Status: `404 Not Found`

```json
{
  "error": "PRODUCT_NOT_FOUND",
  "message": "Product was not found for the provided ID."
}
```

---

## ERP integration failure

Status: `503 Service Unavailable`

```json
{
  "error": "ERP_UNAVAILABLE",
  "message": "Could not connect to the external ERP API."
}
```

---

## Invalid request

Status: `400 Bad Request`

```json
{
  "error": "INVALID_REQUEST",
  "message": "Required fields are missing or invalid."
}
```
