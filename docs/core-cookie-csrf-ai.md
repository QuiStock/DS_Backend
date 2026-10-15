# Cookie authentication, CSRF and AI chat

The Core validates the same RS256 access JWT whether it arrives in
`Authorization: Bearer <token>` or the HTTP-only `access_token` cookie.
Its existing issuer, audience, expiry, subject and email checks apply to both.
Requests containing both an Authorization header and an access cookie, or
duplicate access cookies, are rejected with HTTP 400. Missing or invalid access
tokens return 401. A refresh token cannot authenticate a Core request.

## Core CSRF flow

All unsafe methods (POST, PUT, PATCH and DELETE) authenticated by access cookie
require the CSRF cookie/header pair. Bearer-only clients are exempt.
CSRF checks run before business controllers; invalid or missing CSRF returns 403.

1. Log in through Auth, preserving both HTTP-only authentication cookies.
2. Call authenticated `GET /csrf` (public gateway path `/api/csrf`).
3. Preserve the returned HTTP-only `XSRF-TOKEN` cookie and read the JSON:
   `{"headerName":"X-XSRF-TOKEN","token":"..."}`.
4. Include that token in the `X-XSRF-TOKEN` request header on unsafe requests,
   along with the browser/native cookie jar. Use the returned token unchanged.
5. On access expiry, refresh through Auth. Core does not consume refresh tokens
   or silently refresh requests.

The CSRF response has `Cache-Control: no-store`. Authentication cookies remain
HTTP-only; the endpoint exposes only a CSRF token, never access/refresh tokens.

Auth uses its own application-relative `GET /csrf`, `AUTH-XSRF-TOKEN` cookie and `X-CSRF-TOKEN` header
for cookie-authenticated refresh/logout. Obtain the Auth CSRF token for those
operations, not the Core token. Login continues to use email, password and platform.

## Configuration

| Variable | Default / purpose |
| --- | --- |
| `CORS_ALLOWED_ORIGINS` | Empty means same-origin only. Comma-separated exact frontend origins, including scheme and port. Wildcard origins fail startup. Configure this in both APIs when frontend origins differ or proxies preserve the frontend Origin header. |
| `CSRF_COOKIE_SECURE` | Falls back to `COOKIE_SECURE`, then `true`; use `false` only for Core on local HTTP. |
| `CSRF_COOKIE_SAME_SITE` | `Lax`; accepts Lax, Strict or None. None requires Secure. Align with Auth when direct cross-site access is intentional. |
| `AI_API_BASE_URL` | AI host, optionally with its deployment prefix; omit the conversation route. Blank returns 503 `AI_NOT_CONFIGURED` from chat. |
| `AI_CONNECT_TIMEOUT_MS` | `5000`, positive connection timeout. |
| `AI_READ_TIMEOUT_MS` | `60000`, positive response timeout. Align with the deployed gateway timeout. |

A gateway may route `/api` to Core and `/auth` to Auth. With direct Render hosts,
use each service origin without these gateway prefixes; Auth serves `/login`,
`/refresh`, `/logout` and `/csrf` at its application root.
Controllers use application-relative paths; configure the gateway/context path
once to avoid doubled prefixes. Proxies must preserve Cookie and all Set-Cookie
headers. A proxy is outside this implementation's scope. Browser cookie storage
and third-party blocking still require browser/deployment validation.

## Chat contract (breaking response change)

`POST /chat` replaces the former rule-based prototype.

```json
{
  "message": "Which products should I monitor?",
  "conversation_id": "existing-conversation-string",
  "is_resuming_conversation": false
}
```

- `message` is required, nonblank and limited to 4,000 characters.
- `conversation_id` is optional for a new conversation: Core generates a UUID
  string and returns it. Continuation must reuse that returned ID. Supplied IDs
  may be any nonblank string of at most 200 characters.
- `is_resuming_conversation` defaults to false. True requires an existing ID
  and is sent only on the first new message after selecting an ended conversation.
- Core generates `sent_at` from its clock as an ISO-8601 UTC instant. Caller
  identity and timestamps are not forwarded from input.
- Core calls `POST /api/v1/conversations/{id}/messages` on AI with the validated
  access JWT as Bearer. It does not forward browser cookies or refresh tokens.
- Core does not write conversation history to PostgreSQL; AI retains ownership.
  History retrieval, listing and end-conversation proxy routes are not introduced
  in this step.

Success/controlled graph response:

```json
{
  "conversation_id": "existing-conversation-string",
  "request_id": "ai-request-id",
  "response": "AI response",
  "status": "success"
}
```

The old `answer`, `responsible_agent` and `referenced_data` fields are removed.
Supported statuses are success, rejected, clarification_required, out_of_scope
and error. AI guardrail rejection is preserved as HTTP 422 with status rejected.
An application status error returned by AI with HTTP 200 remains HTTP 200.

| Condition | Core HTTP status |
| --- | --- |
| Invalid input / resume without ID | 400 |
| AI rejects JWT / permission / missing conversation / state conflict | 401 / 403 / 404 / 409 |
| AI rate limit | 429 |
| AI missing configuration / connection unavailable | 503 |
| AI timeout | 504 |
| AI 5xx, malformed body, unsupported status or mismatched conversation ID | 502 |

Transport errors use the Core error envelope `{"error":"CODE","message":"..."}`;
raw upstream bodies and tokens are not exposed. Chat responses are not cached.
No automatic retry is performed for POST messages, avoiding duplicate turns.

## Verification

`CookieChatApiIntegrationTest` starts the real Core HTTP server and independent
JWKS/AI HTTP fixtures. It signs real RS256 JWTs and exercises cookie/Bearer,
CSRF, CORS, input validation, forwarding, resume and upstream failures.
`ChatServiceTest` covers the fixed server clock, missing configuration,
connection failures and an actual transport read timeout.
The existing PostgreSQL integration suite still covers operational routes.

Run the repository CI gates:
`./gradlew spotlessCheck checkstyleMain pmdMain assemble test jacocoTestCoverageVerification`.
Also run the configurable-context suite with `-ProutingTestContext=/migration`.
These fixtures verify the AI contract, not the colleague's deployed AI service.
The AI deployment must accept RS256/JWKS before live integration can succeed.

### Historical local validation on 2026-10-12 (before the current main merge)

| Repository | Tests per run | Failures / skipped | Line coverage |
| --- | --- | --- | --- |
| Core | 122 | 0 / 0 | 90.05% |
| Auth | 22 | 0 / 0 | 83.59% |

Both complete suites passed with the default context and `/migration`.
Spotless, Checkstyle, PMD, application assembly and the existing 80% coverage
gate passed in both repositories, without new coverage exclusions. Auth tests
use PostgreSQL and a MongoDB replica set; Core persistence tests use PostgreSQL.
Web, Mobile, proxy setup and deployment/browser validation remain separate work.
