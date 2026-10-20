# OneStopBank Web (`frontend/`)

React frontend for the BankingPortal API. Vite + React 18 + TypeScript (strict), React Router v6, TanStack Query v5,
react-hook-form + zod, Tailwind CSS, Recharts, MSW v2, Vitest + Testing Library, Playwright.

```bash
cd frontend
npm ci
npm run dev        # http://localhost:5173, mock mode (no backend needed)
```

| Script              | What it does                                                    |
| ------------------- | --------------------------------------------------------------- |
| `npm run dev`       | Vite dev server (mock mode unless `VITE_API_MODE=live`)         |
| `npm run build`     | Production build to `dist/`                                     |
| `npm run preview`   | Serve `dist/` on http://localhost:4173                          |
| `npm run lint`      | ESLint (flat config, zero warnings)                             |
| `npm run typecheck` | `tsc --noEmit`                                                  |
| `npm test`          | Vitest (client parsing + mock handler rules)                    |
| `npm run e2e`       | Playwright smoke test (builds, runs `preview` in mock mode)     |

CI: [`.github/workflows/frontend.yml`](../.github/workflows/frontend.yml) runs all of the above on PRs touching `frontend/**`.

## Mock vs live mode

| `VITE_API_MODE`    | Behavior                                                                                                    |
| ------------------ | ----------------------------------------------------------------------------------------------------------- |
| `mock` (default)   | The MSW service worker intercepts `/api/**` in the browser. State is in memory; a full page reload resets it. |
| `live`             | MSW is not loaded. The Vite dev server proxies `/api` to `VITE_API_TARGET` (default `http://localhost:8180`). |

```bash
VITE_API_MODE=live npm run dev                                         # Spring Boot on :8180
VITE_API_MODE=live VITE_API_TARGET=http://localhost:9090 npm run dev   # custom target
```

The backend serves every route under `/api` already, so the proxy does not rewrite paths.
See [`.env.example`](./.env.example).

In mock mode `window.__resetMockDb()` restores the seed data from the browser console. Tests use `resetMockDb()` from
`src/mocks/db.ts` (called automatically after each Vitest test).

## Seeded credentials (mock mode)

| User       | Login (email or account no.)  | Password    | Account  | PIN      | Balance    |
| ---------- | ----------------------------- | ----------- | -------- | -------- | ---------- |
| Jane Doe   | `jane@demo.bank` / `7F3K9Q`   | `Demo@1234` | `7F3K9Q` | `1234`   | $48,200.00 |
| John Smith | `john@demo.bank` / `2M8PLX`   | `Demo@1234` | `2M8PLX` | _none_   | $6,400.00  |

- Jane has 40 transactions over the last 90 days (deposits, withdrawals, transfers to John, credits from `9QW2RT`).
- John has no PIN, so every money movement returns `401 PIN has not been created for this account` until a PIN is created.
- OTP: any 6 digits are accepted. The "sent" OTP `123456` is logged to the browser console. Three sends per account,
  then `OTP generation limit exceeded...` (same as the backend).

## Folder layout and Wave 2 ownership

```
src/
  api/          types.ts, client.ts, endpoints.ts (typed functions + TanStack hooks)    Wave 1 (shared, do not edit)
  mocks/        MSW handlers, in-memory db, fixtures                                    Wave 1 (shared, do not edit)
  app/          router, routes.tsx, AuthProvider, RequireAuth, layouts, toasts          Wave 1 (shared, do not edit)
  ui/           Button, Input, Field, Card, Modal, Table, Badge, MoneyText, ...         Wave 1 (shared, do not edit)
  lib/          money/format helpers                                                    Wave 1 (shared, do not edit)
  features/
    auth/          /login, /register, /forgot-password                                  Wave 2 · 2A Auth
    profile/       /profile                                                             Wave 2 · 2C Money movement + Security + Profile
    dashboard/     /                                                                    Wave 2 · 2B Dashboard + Transactions
    transactions/  /transactions                                                        Wave 2 · 2B Dashboard + Transactions
    payments/      /move-money/{deposit,withdraw,transfer}                              Wave 2 · 2C Money movement + Security + Profile
    security/      /security (PIN)                                                      Wave 2 · 2C Money movement + Security + Profile
```

Rules for Wave 2:

- 2A Auth owns `features/auth/**`; 2B owns `features/dashboard/**` + `features/transactions/**`; 2C owns
  `features/payments/**`, `features/security/**` and `features/profile/**`. Logout is already wired in the top bar
  (`useAuth().signOut()`); 2A can reuse it from its own pages.
- Only edit files inside your own `src/features/<name>/` folders. Each folder exports its pages from `index.ts`; the
  route table in `src/app/routes.tsx` already imports them, so keep the exported names.
- Talk to the API only through `@/api/endpoints` hooks/functions. If you need something new in a shared folder, call it
  out in your PR rather than editing it in parallel.
- Feature-local components, schemas and tests live in the feature folder (e.g. `features/payments/AmountForm.tsx`).

## API quirks (mirrored by the mocks)

- **Response bodies are strings.** Success bodies are usually JSON text (`{"msg": "Cash deposited successfully"}`,
  `{ "token": "..." }`); `client.ts` tries `JSON.parse` and falls back to raw text.
- **Errors are plain text** with the status from `GlobalExceptionHandler`, e.g. `400 Insufficient balance`,
  `400 Amount must be in multiples of 100`, `404 Account does not exist`, `401 Invalid PIN`. The client throws
  `ApiError { status, message }`; display `getErrorMessage(err)`.
- **401 has two meanings.** Business 401s (`Invalid PIN`, `Invalid password`, `Bad credentials`, `Invalid OTP`, PIN not
  created/already created, empty PIN/password) are ordinary errors. Any other 401 on an authenticated call (bad/expired
  token, revoked after logout) clears the token and redirects to `/login`.
- **Amount rules** (`AccountServiceImpl#validateAmount`): `> 0`, a multiple of 100, `<= 100,000`. Checked after the PIN.
  Withdrawals and transfers also need a sufficient balance. Shared client-side check: `validateAmount()` in `@/lib/money`.
- **PIN** is exactly 4 digits. Create needs the password; update needs the password and the old PIN.
- **Idempotency cache.** Deposit, withdraw, transfer and PIN create/update are `@Cacheable` on
  `account + endpoint + request body`. Repeating an identical request returns the cached success **without** moving
  money again (the mock sets `X-Mock-Idempotent-Replay: true`). Failures are not cached.
  Two genuine identical withdrawals in a row therefore count once; the UI should tell the user.
- **Transfers** create a `CASH_TRANSFER` row in the sender's history and a `CASH_CREDIT` row in the receiver's.
  Deposits/withdrawals use target account `N/A`.
- **`GET /account/pin/check`** returns a sentence; `checkPin()`/`usePinStatus()` map it to a boolean
  (`"PIN has been created"`).
- **`GET /users/logout`** answers with a redirect. `logout()` treats any 2xx/3xx/opaque redirect as success and always
  clears the token locally.
- **`POST /users/update`** needs the current password in the body (it is used to authenticate, not to change it).
- Transaction dates are epoch milliseconds.
