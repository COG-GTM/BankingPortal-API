# Credential Policy: Passwords and PINs

This document describes the strength rules enforced for passwords and PINs, the
offline breach-list check, and the supported rotation flows. The implementation
lives in `com.webapp.bankingportal.security.CredentialPolicy`.

## Password strength

Enforced on registration, password change, and password reset.

| Rule | Error message |
| --- | --- |
| Not empty | `Password cannot be empty` |
| 8 to 127 characters | `Password must be at least 8 characters long` / `Password must be less than 128 characters long` |
| No whitespace | `Password cannot contain any whitespace characters` |
| At least one uppercase letter, lowercase letter, digit, and special character | `Password must contain at least ...` |
| Not a common or breached password (see below) | `Password is too common or has appeared in known data breaches` |
| Does not contain the user's name parts or email local part (4+ characters, case-insensitive, leetspeak-aware) | `Password must not contain your name or email` |
| Not the same as the current password (change/reset only) | `New password must be different from the current password` |

### Offline breach-list check

No external service is called. Passwords are checked against a bundled
blocklist at `src/main/resources/security/common-passwords.txt`, which is a
curated list of the most common and most frequently breached passwords, plus
some banking-specific terms such as `onestopbank`.

The blocklist match is intentionally fuzzy so that decorated variants of a weak
base word are rejected too. A password is rejected if any of these forms is on
the list:

1. The lowercased password.
2. The lowercased password with leading and trailing digits and symbols
   removed, e.g. `Summer2024!` becomes `summer`.
3. Form 2 with common leetspeak undone (`@→a 4→a 3→e 1→i !→i 0→o $→s 5→s 7→t`),
   e.g. `P@ssw0rd123!` becomes `password`.
4. Leetspeak undone before stripping, e.g. `0nestopBank!` becomes `onestopbank`.

To extend the list, append lowercase entries, one per line. Lines starting with
`#` are ignored. For stronger coverage you could drop in a vetted local subset
of a public breach corpus, for example the top 10k to 100k entries from
SecLists or an HIBP Pwned Passwords export. The file is loaded once at class
initialization.

## PIN strength

PINs are still 4 digits. That keeps the existing API and UI contract. PINs
are enforced on PIN create and PIN update.

| Rule | Example rejected | Error message |
| --- | --- | --- |
| Not empty | | `PIN cannot be empty` |
| Exactly 4 digits | `123`, `12a4` | `PIN must be 4 digits` |
| Not all the same digit | `0000`, `7777` | `PIN is too easy to guess ...` |
| Not an ascending or descending run | `1234`, `6789`, `4321` | same |
| Not an `ABAB` or `AABB` pattern | `1212`, `6969`, `1122` | same |
| Not a plausible year from 1940 to 2039 | `1990`, `2024` | same |
| Not on the common-PIN list (`security/common-pins.txt`), which covers keypad patterns such as `2580` and other popular picks | `2580`, `1004` | same |
| Not the last 4 digits of the user's phone number | | `PIN must not match the last digits of your phone number` |
| Not the same as the current PIN (update only) | | `New PIN must be different from the current PIN` |

## Rotation

### Tracking

- `User.passwordChangedAt` is set on registration, password change, and
  password reset.
- `Account.pinChangedAt` is set on PIN create and PIN update.

Both columns are nullable and are added by `spring.jpa.hibernate.ddl-auto=update`.
Rows created before this change have `null`, which is reported as rotation due.

Maximum ages can be configured, and these are the defaults:

```properties
banking.credentials.password-max-age-days=90
banking.credentials.pin-max-age-days=180
```

Rotation is **advisory**. Login and transactions are not blocked when a
credential is past its maximum age. Clients should call the status endpoint and
prompt the user.

### Check rotation status

`GET /api/users/credentials/status` requires authentication.

```json
{
  "passwordChangedAt": "2026-06-01T10:15:30",
  "passwordRotationDue": true,
  "pinCreated": true,
  "pinChangedAt": "2026-09-01T08:00:00",
  "pinRotationDue": false
}
```

Fields that are `null`, such as `pinChangedAt` for an account without a PIN,
are left out of the response.

### Rotate password (authenticated)

`POST /api/users/password/change`

```json
{ "currentPassword": "...", "newPassword": "..." }
```

1. The current password is verified. If it is wrong, the response is `401`.
2. The new password is checked against the full password policy and must not
   equal the current password. If it fails, the response is `400` with the
   policy message.
3. The new hash and `passwordChangedAt` are saved.
4. **All existing JWT sessions for the account are revoked**, including the
   one used for this request. The client must log in again. The response is
   `200 {"message": "Password changed successfully. Please log in again."}`.

### Rotate password (forgotten, via OTP)

This is the existing flow, which now enforces the policy:

1. `POST /api/auth/password-reset/send-otp` with `{ "identifier" }`
2. `POST /api/auth/password-reset/verify-otp` with `{ "identifier", "otp" }`,
   which returns `passwordResetToken`
3. `POST /api/auth/password-reset` with `{ "identifier", "resetToken", "newPassword" }`

The new password is validated **before** the single-use reset token is used up.
A request that fails the policy does not burn the token. On success,
`passwordChangedAt` is updated and all JWT sessions are revoked.

### Rotate PIN

`POST /api/account/pin/update` requires authentication.

```json
{ "oldPin": "...", "newPin": "...", "password": "..." }
```

The password and the old PIN are verified. The new PIN is checked against the
PIN policy and must be different from the old PIN. Then the new hash and
`pinChangedAt` are saved.

## Assumptions and non-goals

- There is no password history beyond the current password, so users can
  return to an older password. See the next steps below.
- There is no forced-rotation lockout, because rotation status is advisory.
- PIN length stays at 4 digits to stay compatible with the existing UI.

## Possible next steps

- Store the last N password and PIN hashes and reject any reuse.
- Enforce rotation. For example, issue a restricted token that can only call
  change-password once the maximum age has passed.
- Replace the bundled list with a larger, periodically refreshed local breach
  corpus, for example a k-anonymity SHA-1 prefix file from HIBP.
- Send an email notification when a password or PIN changes.
