> Historical deployment snapshot: these probes preceded the Auth root-route deployment. The latest DS_Auth main serves POST /login, /refresh and /logout directly, plus GET /csrf in the companion PR. Do not apply the old /auth prefix recommendation below to the new deployment. The reported Render outage was subsequently resolved by the deployment owner.

# Auth deployment diagnostic — 2026-10-15

## Scope and observed results

Tested the public Auth deployment and the JavaScript actually served by the
Website. Passwords and authentication/CSRF token values are omitted.

- Website: `https://quistock.onrender.com`
- Auth: `https://ds-auth-v3rj.onrender.com`
- Website asset inspected: `/assets/index-NegMRN9F.js`

| Probe | Observed result |
| --- | --- |
| Website HTML | 200 |
| POST `/login`, the URL used by the Website | 403, empty body |
| POST `/refresh`, the URL used by the Website | 403, empty body |
| OPTIONS `/auth/login` from the Website origin | 200; exact origin and credentials allowed; Content-Type allowed |
| POST `/auth/login`, Website origin, user 01 | 401 `invalid_credentials` |
| POST `/auth/login`, Website origin, users 02, 03, 10, 20 | 401 `invalid_credentials` for each |
| POST `/auth/login`, platform mobile, user 01 | 401 `invalid_credentials` |
| GET `/auth/csrf`, with and without Origin | 500 `internal_error` |
| POST `/auth/refresh`, no refresh cookie | 401 `invalid_refresh_token` |
| POST `/auth/refresh`, synthetic invalid refresh cookie, no CSRF | 401 `invalid_refresh_token` |
| POST `/auth/logout`, no session | 204; both authentication cookies expired with Max-Age=0 |
| GET `/.well-known/jwks.json` | 200; public RSA key, kid=prod, alg=RS256, no private key field |
| GET `/health` | 200, status UP |

Only five distinct accounts were tested. The local Auth configuration limits
login requests to 10 per 15 minutes per resolved IP, so this investigation did
not perform a 20-account login sweep or bypass rate limits. One refresh probe
had a transport reset; a subsequent retry returned the 401 recorded above.

## Confirmed cause of the Website's 403

The published JavaScript creates an Axios client with:

```javascript
baseURL: "https://ds-auth-v3rj.onrender.com"
withCredentials: true
```

It posts to `/login`, `/refresh`, and `/logout`. The Auth contract exposes
`/auth/login`, `/auth/refresh`, and `/auth/logout`. Unknown URLs are denied by
the security filter before reaching a controller. This reproduces the reported
403 without depending on the user's account or role.

Fix the Website client using one consistent convention, for example:

```javascript
baseURL: "https://ds-auth-v3rj.onrender.com/auth"
// Keep post("/login"), post("/refresh"), and post("/logout").
```

Publish a new Website build after changing its configuration/source. Do not
also add `/auth` to these request paths if it is already in baseURL.

`POST /auth/login` is public with respect to JWT/session authentication. Its
JSON request contains email, password, and platform. Successful authentication
still requires valid credentials, an ACTIVE account, and an allowed platform.
Do not weaken the security rules to make an incorrect `/login` URL work.

## Separate issues still requiring investigation

### Account validation returns 401

The correct login endpoint rejects the supplied credentials for users 01, 02,
03, 10 and 20. No `platform_not_allowed` response was observed. This does not
establish which of the following is wrong: account presence, account status,
password/hash, role linkage, or the database used by the deployed Auth.
The generic response intentionally does not disclose those details.

Run this read-only query against the database actually configured in Render's
Auth service to check all 20 account records and their role linkage. It does
not return password hashes or test the password itself:

```sql
WITH expected AS (
    SELECT 'finalv1.usuario.' || lpad(n::text, 2, '0') || '@quistock.teste' AS email
    FROM generate_series(1, 20) AS numbers(n)
)
SELECT e.email,
       u.id AS user_id,
       u.status,
       r.code AS role_code,
       u.password_hash LIKE '$2%' AS bcrypt_prefix_present
FROM expected e
LEFT JOIN user_account u ON lower(btrim(u.email)) = e.email
LEFT JOIN role r ON r.id = u.role_id
ORDER BY e.email;
```

Do not change the password or status merely to suppress the error; verify the
intended test data and correct database first.

### CSRF endpoint returns 500

`GET /auth/csrf` fails even without an Origin header. The invalid-refresh-cookie
probe also reached refresh-token validation without a CSRF header. The local
implementation is expected to return 403 for that cookie-authenticated refresh
request before consuming/revoking a session.

These observations show a difference from the locally tested CSRF contract.
They do not prove whether the cause is the deployed revision, its security
configuration, cookie settings, or controller token resolution. Render runtime
logs and the deployed revision/configuration are needed to establish the cause.
The Render connector was unavailable during this investigation.

### Browser session remains unverified

Logout expiration cookies used Secure, HttpOnly, Path=/, and SameSite=Lax.
No successful login was obtained, so browser cookie storage, authenticated Core
calls, refresh rotation and Chatbot integration could not be validated end to
end. Re-test them after correcting the Website URL and account data, and after
fixing the deployed CSRF failure.

No users, password hashes, deployed environment variables or application
security policies were changed by these tests.
