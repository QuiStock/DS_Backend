# QuiStock API Contract

Version: 2.1.0
Last updated: 2026-10-20

## Base URL

Local:

`http://localhost:8080`

---

## Data source

The data returned by the API originates from an external ERP API.

For the MVP, the ERP is represented by a MockAPI containing fake data with the same
structure as a real ERP integration.

The external endpoint consumed by the backend is `GET /products`. Each returned record
represents an ERP batch, not a consolidated product. The backend periodically synchronizes
the response into PostgreSQL, groups batches by product and branch, and serves the saved
snapshot to the frontend. When no snapshot exists, the first product read triggers a sync.

The external MockAPI currently uses Portuguese field names. Those names are kept only at
the integration boundary; all public API fields and internal code identifiers are English.

## Authentication

All operational API routes require an RS256 access JWT, supplied either in
`Authorization: Bearer <access_token>` or the HTTP-only `access_token` cookie.
Both use the same JWKS, issuer, audience, expiration, positive SQL `sub`, and
nonblank `email` validation. Missing/invalid tokens return 401. Combining an
Authorization header and access cookie, or duplicate access cookies, returns 400.
Refresh cookies never authenticate Core requests.

Cookie-authenticated unsafe requests require a CSRF cookie/header pair:
authenticated `GET /csrf` returns `{"headerName":"X-XSRF-TOKEN","token":"..."}`
and sets `XSRF-TOKEN`. Send both that cookie and the returned
`X-XSRF-TOKEN` header on POST/PUT/PATCH/DELETE. Missing or invalid CSRF returns
403. Bearer-only requests are exempt. CSRF responses are not cached. Configure `CORS_ALLOWED_ORIGINS` with exact frontend origins, `CSRF_COOKIE_SECURE` (falls back to `COOKIE_SECURE`) and `CSRF_COOKIE_SAME_SITE`. See [the integration guide](docs/core-cookie-csrf-ai.md).

The separate DS_Auth service issues the cookie and remains responsible for login, refresh, and
logout. Core acts as an OAuth2 resource server and accepts RS256 JWTs. It validates the token signature
through the configured JWKS URL, as well as its issuer, audience, expiration, `sub`, and
`email` claims. `sub` must be a
positive numeric ID matching `user_account.id` in PostgreSQL; `email` must contain the account
email. The expected audience is `quistock-api` by default. Configure `AUTH_JWT_ISSUER`,
`AUTH_JWT_JWK_SET_URI`, and `AUTH_JWT_AUDIENCE` for the authentication service.

Missing, invalid, or expired tokens return `401 Unauthorized`. Actor IDs written for suggestion
creation, triage, decisions, and logs come from the authenticated `sub`, never from a request
body. Core looks up the active account and role in PostgreSQL for store and user-management authorization;
it does not trust a role claim from the JWT.

The Auth `access_token` cookie must also be in scope for the Core request: deploy Auth and Core
behind the same host/gateway or configure a shared parent cookie domain and `Path=/` in Auth.
CORS alone cannot make a cookie set for a different host available to Core. The existing Bearer
header remains supported for clients that send it explicitly.

### Store filters and authorization

The following GET routes accept an optional `store_id` query parameter: `/products`,
`/flows`, `/actions`, `/dashboard/summary`, and `/managers`.

`store_id` identifies **one active store** by its positive numeric SQL `store.id`, returned
as `store_id` by `/branches`. It is not the ERP branch code (`/branches[].id`).
Without the filter, Core consolidates only the caller's permitted active stores:

| SQL role | Store scope |
| --- | --- |
| `ADMIN` | All active stores. |
| `GERENTE_REGIONAL` | Active stores in their assigned active region. |
| `GERENTE` | Their single actively assigned store. |
| `FUNCIONARIO` | Their single actively assigned store. |

The SQL account must be active. Missing/inactive required assignments or unsupported roles
return `403`. An explicit nonexistent, inactive, or unauthorized `store_id` returns `403`;
nonpositive or nonnumeric values return `400`. No matching records in an allowed store
return an empty list, or zero dashboard metrics. No query falls back to global data.

Existing endpoint role restrictions still apply: `/managers` is restricted to `ADMIN` and
`GERENTE_REGIONAL`; `/team-members` remains restricted to `GERENTE` and is already scoped
to that manager's active store, so it does not require a store selector.

All query filters are combined with AND. The legacy product `branch` filter (store name
or ERP code) remains supported and cannot broaden the authorized store scope. Combining
incompatible filters returns an empty list.

Resource operations resolve the store from the persisted record, not from a submitted filter:
`GET /products/{id}`, `POST /flows/analyze`, `POST /actions/generate`, and
`PATCH /actions/{id}/status` return `404` for missing or out-of-scope resources. Action
scope follows `suggestion.store_id`, including suggestions without a product analysis.

Inactive stores are excluded from operational queries, including analysis/action lists and
dashboard metrics. No general `include_inactive` parameter is introduced. Existing user
management detail routes retain their specific history rules; inactive manager accounts
remain visible in lists when their last assigned store is active and permitted.

`/branches` returns exactly the caller's permitted active stores. `/profile`, `/regions`,
`/csrf`, health, Swagger, `/erp-integration/status`, and the separate IA chat integration
do not acquire a `store_id` filter. Store filtering requires no Auth or schema changes.

Examples:

```text
GET /dashboard/summary?store_id=17
GET /products?store_id=17&category=Dairy
GET /flows?store_id=17&flow_type=HIGH
GET /actions?store_id=17&status=GENERATED
GET /managers?store_id=17
```

### Role access

| Role | User-management access |
| --- | --- |
| `ADMIN` | Manage all Gerentes. |
| `GERENTE` | Manage only `FUNCIONARIO` accounts assigned to the Gerente's store. |
| `GERENTE_REGIONAL` | List, create, edit and deactivate Gerentes assigned to stores in the regional manager's active region. |
| `FUNCIONARIO` | No manager/team CRUD access. |

Before first use, the database must contain at least one active `ADMIN` account created through
the approved bootstrap process. There is deliberately no public endpoint for creating the first
ADM.

Accounts are deactivated with `status: "INACTIVE"`; user records are not deleted. A Gerente
with active employees in the store requires an active replacement Gerente in the same store
selected by an ADM or the responsible Regional Manager. Employees stay assigned to the store;
`created_by_id` always preserves who originally created each account.

---

# 0. User profiles and management

These routes read and write account/profile data in PostgreSQL. Passwords supplied during
provisioning are stored only as BCrypt hashes with cost 12; neither responses nor logs contain
the password or hash. Login and token/session operations remain in DS_Auth.

## 0.1 Read the current profile

Method: `GET /profile`

Returns the SQL profile belonging to the authenticated JWT `sub`.

Response 200:

```json
{
  "id": "42",
  "name": "Ana Silva",
  "email": "ana@example.com",
  "role": "GERENTE",
  "status": "ACTIVE",
  "profile_photo_url": null,
  "store_id": 17,
  "store_code": "FIL001",
  "store_name": "Filial Centro",
  "region_id": 3,
  "region_code": "REGION-NORTH",
  "region_name": "REGION-NORTH"
}
```

An inactive or missing account returns `403 Forbidden`.

## 0.2 List regions

Method: `GET /regions`

Available to `ADMIN`, `GERENTE` and `GERENTE_REGIONAL`. Only active ERP-backed regions are
returned; a Regional Manager receives only their own active region. `id` is the internal SQL identifier.

```json
[{"id":"3","code":"REGION-NORTH","name":"REGION-NORTH"}]
```

The ERP currently supplies `region_id` but no display name, so `name` initially mirrors the
ERP code.

## 0.3 Manage Gerentes (ADM and Regional Manager web)

All routes require an active `ADMIN` or `GERENTE_REGIONAL`. The ADM has global access; a
Regional Manager must have an active region assignment and can only access Gerentes in that
region, determined by `user_store → store.region_id`. Out-of-scope items return `404`;
attempting to assign a store outside the region returns `403`.

| Method and route | Behavior |
| --- | --- |
| `GET /managers` | List active and inactive Gerentes in permitted active stores; optional `store_id`. |
| `POST /managers` | Create a Gerente with a generic initial password. |
| `GET /managers/{id}` | Read one Gerente. |
| `PATCH /managers/{id}` | Update name/email/store or activate/deactivate the Gerente. |

Create request:

```json
{"name":"Joao Silva","email":"joao@example.com","password":"InitialPass123","store_id":17}
```

`store_id` is required and must identify an active store with an active region. Each active
Gerente has exactly one active store assignment. Reassignment closes the old assignment and
creates a history row; reactivation requires an explicit `store_id`.

When a Gerente's store has active employees, deactivation requires an active replacement
Gerente assigned to the same store. Core closes the old Gerente's active store assignment and
marks the account inactive in one transaction. Employees stay in the store, and account
creation authors are not rewritten:

```json
{"status":"INACTIVE","replacement_manager_id":42}
```

No `DELETE` route is provided; deactivation preserves account and assignment history.
Inactive accounts are scoped using their most recent store assignment, allowing the
responsible Regional Manager to read and reactivate them. Existing Gerentes without a store
must be assigned by an ADM before they can access the team API. No new schema objects are required.

## 0.4 Manage team members (Gerente web)

All routes require an active `GERENTE` with an active store assignment. The list and item
routes return only `FUNCIONARIO` accounts in that store, regardless of who created them.
For inactive employees, the most recent store assignment determines the scope.

| Method and route | Behavior |
| --- | --- |
| `GET /team-members` | List store employees, including inactive accounts. |
| `POST /team-members` | Create a `FUNCIONARIO` in the Gerente's store. |
| `GET /team-members/{id}` | Read one employee in the Gerente's store. |
| `PATCH /team-members/{id}` | Update name/email/status or assignment. |

Employee request (the `store_id` comes from `GET /branches`):

```json
{
  "name":"Maria Souza",
  "email":"maria@example.com",
  "password":"InitialPass123",
  "role":"FUNCIONARIO",
  "store_id":17
}
```

A `FUNCIONARIO` must have exactly one active store assignment. Submitted `store_id` must match
the authenticated Gerente's store; cross-store assignments return `403`. Deactivation closes
the active assignment; reactivation requires an explicit `store_id` for that same store.
`GERENTE_REGIONAL` and `region_id` are rejected with `400` by `/team-members`.
Regional Manager accounts are provisioned separately through the administrative process;
each has one active region assignment, and a region has only one active Regional Manager.

Create returns `201`; reads/updates return `200`. Invalid fields or missing required assignments
return `400`, missing users/branches/regions or out-of-scope team members return `404`, duplicate
email or conflicting active assignment returns `409`, and role/scope violations return `403`.

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
    "current_stock": 121.000,
    "minimum_stock": 40.000,
    "sales_7d": 50.000,
    "sales_30d": 200.000,
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
- `sku` is limited to 100 characters. The snapshot is rejected if an ERP product code exceeds
  this database/API constraint.
- `name` receives `nome_produto`.
- `category` receives `categoria`.
- `branch` receives `filial`.
- `current_stock` is the decimal sum of `quantidade` for active batches in the saved snapshot.
- `sales_7d` is the decimal sum of `vendas_7d` for all batches in the group.
- `sales_30d` is the decimal sum of `vendas_30d` for all batches in the group.
- `expiration_days` is calculated using the nearest expiration date among batches with
  quantity greater than zero.
- `minimum_stock` is not summed. If batches disagree, the highest valid value is saved for
  the product and branch.
- `supplier_lead_time` receives `lead_time_dias` and is not summed. If batches disagree,
  the highest valid value is saved for the product and branch.
- `price`, `cost`, and `last_restock` come from the most recent batch according to `data_entrada`.
- `status` is `true` when consolidated stock is greater than zero.
- `id` is deterministic from the ERP identifiers:
  `codigo_produto_erp:codigo_filial_erp`.

The ERP contract requires each batch to include `data_validade`, `preco`, and `custo`;
price and cost must be numeric and non-negative. If any batch violates these requirements,
the snapshot is rejected and the previously persisted snapshot remains active. The ERP-provided
`id` is preferred as the batch identifier, with `num_lote` as a fallback; the lot number may
repeat because the persisted batch key also includes product and branch identifiers.

The API preserves decimal quantities from PostgreSQL `NUMERIC(14,3)` fields. The ERP's
7-day and 30-day sales aggregates are stored by product and branch because this endpoint does
not return individual sales records to populate `sale` and `sale_item`.

Internal batch fields such as `num_lote`, `certificado_qualidade`, `data_entrada`,
`data_validade`, and `unidade_medida` are not exposed by this public route.

Dates may be received as ISO-8601 dates, Unix timestamps in seconds, or Unix timestamps in
milliseconds. Numeric values may be JSON numbers or numeric strings. The backend
normalizes these formats before consolidation.

An expired batch may produce a negative `expiration_days` value. The implementation uses
the actual number of days between the current date and the expiration date. The ERP must send
an expiration date on every batch; the field is `null` only when the consolidated product has
no batch with positive stock.

### Optional filters

Store authorization and product/category filters restrict the SQL source rows; the stock
`status` filter is applied after batch consolidation:

```text
GET /products?branch=Downtown%20Store
GET /products?category=Dairy
GET /products?status=true
```

Available parameters:

- `store_id`: positive SQL ID of one permitted active store.
- `branch`: legacy filter by store name or ERP code, within the permitted scope.
- `category`: filters products by category.
- `status`: `true` selects positive consolidated stock; `false` selects zero stock.
  It never includes inactive stores/products.

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
  "last_restock": "2026-08-20T00:00:00Z",
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

- `store_id`: positive SQL ID of one permitted active store.
- `flow_type`: `HIGH`, `MEDIUM`, or `LOW`.
- `product_id`: filters flows for one product.
- `status`: `ANALYZED` in the MVP; filters by analysis status.

---

# 3. Actions

An action is the recommendation generated from a flow.

Possible types:

- `PROMOTION`
- `ORDER`

PostgreSQL also reserves `MONITOR` for the future ML flow. The current rule-based action
generation does not create `MONITOR` actions.

Possible statuses:

- `GENERATED`
- `IN_EMPLOYEE_TRIAGE`
- `SENT_TO_MANAGER`
- `APPROVED`
- `REJECTED`

Generation rules:

- `HIGH` flow generates `ORDER`.
- `MEDIUM` flow does not generate a suggestion; its stock level is adequate.
- `LOW` flow generates `PROMOTION`.

The type and status values match the PostgreSQL enums. A generated action starts with
`GENERATED`. Employee triage and manager decisions use the other statuses above.

---

## 3.1 Generate actions for a flow

Method:

`POST /actions/generate`

Body:

```json
{
  "flow_id": "101",
  "promotion_valid_from": "2026-10-01",
  "promotion_valid_until": "2026-10-07"
}
```

`promotion_valid_from` and `promotion_valid_until` are required when the flow is `LOW` and
therefore generates a promotion. The end date must be on or after the start date. Do not send
these fields for an `ORDER`.

Response 201:

```json
{
  "flow_id": "101",
  "generated_actions": [
    {
      "id": "501",
      "action_type": "PROMOTION",
      "status": "GENERATED",
      "justification": "Expiration or excess-stock risk: create a promotion.",
      "promotion_valid_from": "2026-10-01",
      "promotion_valid_until": "2026-10-07"
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
    "status": "GENERATED",
    "justification": "Expiration or excess-stock risk: create a promotion.",
    "promotion_valid_from": "2026-10-01",
    "promotion_valid_until": "2026-10-07",
    "decision_justification": null
  }
]
```

### Optional filters

```text
GET /actions?status=GENERATED
GET /actions?action_type=PROMOTION
GET /actions?flow_id=101
```

Available parameters:

- `status`: `GENERATED`, `IN_EMPLOYEE_TRIAGE`, `SENT_TO_MANAGER`, `APPROVED`, or `REJECTED`.
- `store_id`: positive SQL ID of one permitted active store.
- `action_type`: `PROMOTION` or `ORDER`.
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
  "status": "APPROVED",
  "final_promotion_valid_from": "2026-10-02",
  "final_promotion_valid_until": "2026-10-08"
}
```

Final promotion dates are optional and may be sent when approving a promotion. Send both or
neither; the end date must be on or after the start date. A rejection must include a non-empty
`justification`:

```json
{
  "status": "REJECTED",
  "justification": "The store cannot run a promotion this week."
}
```

Response 200:

```json
{
  "id": "501",
  "status": "APPROVED",
  "promotion_valid_from": "2026-10-02",
  "promotion_valid_until": "2026-10-08"
}
```

---

# 4. AI chat

`POST /chat` now forwards to the AI conversation API, replacing the former
rule-based prototype. Configure `AI_API_BASE_URL`; a blank value returns 503
`AI_NOT_CONFIGURED` without preventing the other APIs from starting.

Request:

```json
{
  "message": "Which products need attention?",
  "conversation_id": "existing-conversation",
  "is_resuming_conversation": false
}
```

Message is required, nonblank and at most 4,000 characters. Omit conversation_id
to start a conversation; Core generates and returns a UUID string. Reuse the ID
for subsequent messages. Supplied IDs are nonblank strings of up to 200
characters. is_resuming_conversation defaults to false; true requires an
existing ID and is sent for the first new message after selecting an ended
conversation. Core generates sent_at in UTC.

Core sends only message, sent_at and is_resuming_conversation to
`POST /api/v1/conversations/{conversation_id}/messages` with the validated
access JWT as Bearer. It never forwards cookies or caller-supplied identity.

Response 200 (breaking replacement of answer/responsible_agent/referenced_data):

```json
{
  "conversation_id": "existing-conversation",
  "request_id": "ai-request-id",
  "response": "AI response",
  "status": "success"
}
```

Allowed statuses: success, rejected, clarification_required, out_of_scope, error.
An AI guardrail rejection returns the same response structure with HTTP 422 and
status rejected. Upstream authorization/not-found/conflict/rate-limit responses
map to 401/403/404/409/429 with sanitized Core errors. Invalid AI responses and
AI server errors return 502; connection failure returns 503; timeout returns 504.
Core validates the returned conversation ID and does not retry message POSTs.

Chat responses are not cached. History is owned by AI; no PostgreSQL chat
persistence or history/list/end routes are added in this phase. See
[the integration guide](docs/core-cookie-csrf-ai.md) for all error codes and
timeout configuration.

---

# 5. Branches

## 5.1 List branches

Method:

`GET /branches`

Requires an active SQL account identified by the authenticated JWT subject. An active
`GERENTE_REGIONAL` receives only active branches in their assigned active region; a missing
or inactive region assignment returns `403`. The region is resolved from SQL, never from
a client-provided parameter or role claim. `ADMIN` receives all active stores;
`GERENTE` and `FUNCIONARIO` receive only their actively assigned store. Missing or inactive
store assignments return `403`. Unsupported roles and inactive accounts also return `403`.

Response 200:

```json
[
  {
    "id": "1",
    "store_id": 17,
    "region_id": 3,
    "name": "Downtown Store",
    "address": null,
    "city": null,
    "state": null,
    "latitude": null,
    "longitude": null
  }
]
```

The ERP provides `region_id`, which the backend synchronizes into the existing region catalog.
It does not currently provide address or coordinates, so those fields are returned as `null`.
`id` remains the ERP branch code for compatibility; `store_id` is the internal SQL ID required
by user assignment routes.
`region_id` is the internal numeric SQL region ID, not the ERP region code. It is nullable
for branches not yet assigned to a region; such branches are not returned to regional managers.

---

# 6. Dashboard

Dashboard metrics are calculated from analyzed products, generated flows, and suggested
actions.

## 6.1 Get the dashboard summary

Method:

`GET /dashboard/summary`

Optional query parameter: `store_id` (one positive numeric SQL store ID). Every metric uses
the same authorized store scope. Without it, the response aggregates permitted active stores.
`total_products` counts analyzed product/store pairs, not the full catalog. Risk metrics use
the most recent analysis per pair (`created_at DESC`, then analysis ID DESC).

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
- `active_actions`: actions with `GENERATED`, `IN_EMPLOYEE_TRIAGE`, `SENT_TO_MANAGER`, or
  `APPROVED` status.

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

## Authentication required

Status: `401 Unauthorized`

Returned when the bearer token is missing, expired, or fails signature/claim validation.

## Product not found

Status: `404 Not Found`

```json
{
  "error": "PRODUCT_NOT_FOUND",
  "message": "Product was not found for the provided ID."
}
```

## Flow not found

Returned by `POST /actions/generate` when the supplied flow ID does not exist.

Status: `404 Not Found`

```json
{
  "error": "FLOW_NOT_FOUND",
  "message": "Flow was not found for the provided ID."
}
```

## Action not found

Returned by `PATCH /actions/{id}/status` when the supplied action ID does not exist.

Status: `404 Not Found`

```json
{
  "error": "ACTION_NOT_FOUND",
  "message": "Action was not found for the provided ID."
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

## Public health probes

`GET /health` and `GET /health/readiness` require no authentication and return
200 for available dependencies or 503 otherwise. Responses include status and
safe diagnostic details. `GET /health/liveness` checks application availability
without accessing PostgreSQL, JWKS or ERP. Use readiness to control traffic and
liveness to decide restarts. Startup failures still prevent these routes from serving.

Dependency results are cached for 5 seconds by default (`HEALTH_CACHE_TTL` overrides
this duration; `0ms` disables caching). Concurrent dependency checks share in-flight
work. Dependency state transitions may remain cached until the configured TTL expires.
