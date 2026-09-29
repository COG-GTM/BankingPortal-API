# Security response headers and CSRF posture

All headers below are configured in `WebSecurityConfig#securityFilterChain` via
Spring Security's `HttpSecurity#headers` DSL, so they are applied by
`HeaderWriterFilter` to every response that passes through the security filter
chain — including 401 responses produced by `JwtAuthenticationFilter` and
`JwtAuthenticationEntryPoint`. They are asserted in `SecurityHeadersTests`.

## Headers

| Header | Value | Why |
| --- | --- | --- |
| `Content-Security-Policy` (API) | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` | API responses are JSON/text and are never meant to be rendered as a document. If a response is ever rendered by a browser (e.g. reflected content in an error body, or a MIME sniffing mistake), nothing can be loaded, executed, framed, or submitted from it. |
| `Content-Security-Policy` (docs: `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` | Swagger UI is an HTML app served by this service, so the locked-down API policy would break it. This policy only allows same-origin resources; `'unsafe-inline'` is limited to styles (Swagger UI sets inline styles) and `data:` to images/fonts (inline SVG icons). Scripts stay same-origin only. |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains` | Tells browsers to only use HTTPS for one year, preventing SSL-stripping / downgrade attacks on a banking API. `preload` is intentionally **not** set: preload-list submission is a domain-wide, hard-to-reverse decision for the domain owner. |
| `X-Frame-Options` | `DENY` | Legacy clickjacking protection for browsers that ignore CSP `frame-ancestors`. Nothing in this service should be framed. |
| `X-Content-Type-Options` | `nosniff` | Stops browsers from MIME-sniffing a JSON/text response into something executable (e.g. HTML or script). |

Spring Security's other defaults (`Cache-Control: no-cache, no-store, ...`,
`X-XSS-Protection: 0`) are left in place.

### HSTS and TLS termination

Per RFC 6797, HSTS is ignored on plain-HTTP responses, and Spring Security only
emits it when `request.isSecure()` is true. If TLS is terminated at a load
balancer / reverse proxy, the app must be told the original scheme, e.g.
`server.forward-headers-strategy=framework` (or `native`) with the proxy sending
`X-Forwarded-Proto: https`. Otherwise the header will not be emitted.
Alternatively, set HSTS at the edge.

## CSRF posture

CSRF protection remains **disabled** (`csrf.disable()`), and that is the correct
choice for this API as it stands:

- CSRF exploits *ambient* credentials — cookies or HTTP auth that the browser
  attaches automatically to a cross-site request.
- This API is stateless (`SessionCreationPolicy.STATELESS`), issues no session or
  auth cookie, and authenticates exclusively from the
  `Authorization: Bearer <jwt>` request header in `JwtAuthenticationFilter`.
- A browser never attaches an `Authorization` header to a cross-site request on
  its own; a malicious page would need to already know the token, at which point
  CSRF is not the attack. Setting that header cross-origin also requires a CORS
  preflight.
- Enabling Spring's `CsrfFilter` would add no protection and would break every
  non-browser/SPA client that doesn't fetch and echo a CSRF token (including
  `POST /api/users/login` and the other public endpoints).

`SecurityHeadersTests` pins this posture: a bearer-authenticated `POST` without
a CSRF token is not rejected with `403`, and an unauthenticated one gets `401`.

### When this must be revisited

Re-enable CSRF protection (e.g. `CookieCsrfTokenRepository` + a token-echoing
client) if any of the following change:

- The JWT (or any credential) is moved into a cookie, e.g. an `HttpOnly`
  "remember me" or session cookie.
- HTTP sessions or form login are introduced.
- HTTP Basic / other browser-managed authentication is enabled.

Note that `GET /api/users/logout` redirects to Spring's `/logout`, which only
accepts `GET` because CSRF is disabled; enabling CSRF would require moving that
flow to `POST`.

Related notes (not changed here): CORS currently allows any origin
(`CorsConfig`). That is safe only because credentials are not cookie-based; it
should be narrowed to known front-end origins before any cookie-based auth is
introduced.
