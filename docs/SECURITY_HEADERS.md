# Security response headers and CSRF posture

All headers below are configured in `WebSecurityConfig#securityFilterChain` via
Spring Security's `headers()` DSL, so they are written by `HeaderWriterFilter` on
every response that passes through the security filter chain — including 401s
from `JwtAuthenticationEntryPoint` and error responses. They are asserted by
`SecurityHeadersTests`.

## Headers

| Header | Value | Why |
| --- | --- | --- |
| `Content-Security-Policy` (API) | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` | The API only returns JSON. If a response is ever rendered by a browser (e.g. an error page reflecting input), nothing can be loaded, executed, framed or submitted from it. |
| `Content-Security-Policy` (Swagger UI: `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` | Swagger UI is an HTML app served by springdoc from the same origin; it needs same-origin scripts, some inline styles, `data:` images/fonts and fetches to the OpenAPI JSON. Scripts stay `'self'`-only (no `'unsafe-inline'`/`'unsafe-eval'`). Exactly one CSP is emitted per response. |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains` | One year, subdomains included. Spring Security only emits it on secure requests (per RFC 6797 it is ignored over plain HTTP). `preload` is deliberately not set — it is hard to undo and should be an explicit ops decision. |
| `X-Frame-Options` | `DENY` | Legacy clickjacking protection for browsers that ignore CSP `frame-ancestors`. Nothing in this API needs to be framed. |
| `X-Content-Type-Options` | `nosniff` | Stops browsers MIME-sniffing JSON responses into something executable. |

Spring Security's other defaults (`Cache-Control: no-cache, no-store, ...`,
`X-XSS-Protection: 0`) are left in place.

### Deployment note (HSTS)

HSTS is only written when the request is seen as HTTPS. If TLS terminates at a
load balancer / reverse proxy, the app must trust the forwarded headers
(e.g. `server.forward-headers-strategy=native` or `framework`, and the proxy
must send `X-Forwarded-Proto: https`), otherwise the header will never be sent.

## CSRF posture: disabled, intentionally

CSRF protection remains disabled (`csrf.disable()`). This is the correct setting
for this API as it is built today:

- **Authentication is a bearer token in the `Authorization` header.** Browsers
  never attach that header automatically to cross-site requests; an attacker's
  page cannot make the victim's browser send it. CSRF relies on ambient
  credentials (cookies, HTTP Basic, client certs), and there are none here.
- **The server is stateless.** `SessionCreationPolicy.STATELESS` means no
  `JSESSIONID` is created or used for authentication, and the app issues no
  auth cookies.
- **CORS does not reintroduce the risk.** `CorsConfig` allows any origin but does
  not enable `allowCredentials`, so cookies are not sent cross-origin anyway —
  and a cross-origin script still cannot obtain the victim's token.
- **Public endpoints** (register, login, OTP/password-reset) are unauthenticated
  by design, so a forged request gains the attacker nothing they could not do
  directly. (Login CSRF is not meaningful without a cookie-based session.)

Enabling Spring's CSRF filter would only break non-browser clients and the UI
without adding protection.

### When to revisit

Re-enable CSRF protection (e.g. `CookieCsrfTokenRepository` +
`CsrfTokenRequestAttributeHandler`) if any of the following change:

- the JWT is moved into a cookie (including `HttpOnly` cookies),
- server-side sessions / `formLogin` / `httpBasic` are introduced,
- `allowCredentials(true)` is added to the CORS configuration.

Relatedly, the frontend should keep the token out of places readable by
injected script where possible; CSP above limits that risk for anything served
by this API, but the UI is a separate origin with its own policy.
