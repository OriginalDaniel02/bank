# Bank API

A REST API for a digital wallet and banking app, in the style of OPay and similar fintech apps. It covers user accounts, JWT authentication, wallet accounts, transfers with a transaction PIN, idempotent retries, limits and fees.

Built with Spring Boot, PostgreSQL and Flyway.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Frontend](#frontend)
- [API Overview](#api-overview)
- [Security Model](#security-model)
- [Card Deposits (Paystack)](#card-deposits-paystack)
- [Trying It in Swagger](#trying-it-in-swagger)
- [KYC and Face Verification](#kyc-and-face-verification)
- [How Transfers Work](#how-transfers-work)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [Roadmap](#roadmap)

## Features

- Sign-up and login with JWT access tokens (15 minutes) and rotating refresh tokens (7 days)
- Wallet accounts with a unique 10-digit account number
- Transfers between accounts, by account id or by account number, with name enquiry
- 4-digit transaction PIN with lockout after repeated wrong attempts
- Idempotent transfers via an `Idempotency-Key` header, so a retry never moves money twice
- Per-transfer and daily limits, and tiered transfer fees credited to an income account
- KYC with face verification (pluggable provider) and tier-based limits: unverified users get stricter limits
- Paged transaction history, plus statements with date and type filters, opening and closing balances, and CSV export
- Saved beneficiaries (recipients)
- Savings goals (flexible or locked until a date), PIN-protected withdrawals
- Financial insights from your real transactions (monthly money in and out, where it went)
- Change password (other devices are signed out)
- Card deposits through Paystack (signed webhook, verified server-side, credited exactly once)
- In-app notifications for debits, credits and KYC results
- Roles (`USER`, `ADMIN`); only admins can credit or debit accounts directly
- Ownership checks: users can only access their own data
- Concurrency-safe balance updates (pessimistic locking in account-id order plus optimistic versioning)
- Database migrations with Flyway
- OpenAPI / Swagger UI documentation

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot (Web MVC, Data JPA, Security, Validation) |
| Database | PostgreSQL 17 (H2 in tests) |
| Migrations | Flyway |
| Auth | JWT (jjwt) and BCrypt |
| Docs | springdoc OpenAPI |
| Build | Maven |
| Containers | Docker and Docker Compose |

## Getting Started

### Prerequisites

- JDK 21
- Maven 3.9+ (or the included wrapper)
- Docker and Docker Compose (for PostgreSQL, or to run everything in containers)

### 1. Clone

```bash
git clone https://github.com/OriginalDaniel02/bank.git
cd bank
```

### 2. Create a `.env` file

```env
POSTGRES_DB=bank
POSTGRES_USER=bank
POSTGRES_PASSWORD=change-me
# Base64 string of at least 32 bytes. Generate one with: openssl rand -base64 32
JWT_SECRET=
# Optional
ADMIN_EMAIL=
FEE_INCOME_ACCOUNT=
```

### 3a. Run everything with Docker

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`.

### 3b. Or run the app locally with only the database in Docker

```bash
docker compose up -d postgres
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

Flyway creates and updates the schema on startup.

### API docs

Swagger UI is at `http://localhost:8080/swagger-ui/index.html` (see [Trying It in Swagger](#trying-it-in-swagger)).

## Configuration

All settings come from environment variables (see `src/main/resources/application.properties`).

| Variable | Required | Description |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Yes | Database credentials |
| `DB_HOST`, `DB_PORT` | No | Database location (default `localhost:5432`) |
| `JWT_SECRET` | Yes | Base64-encoded signing key, at least 32 bytes |
| `ADMIN_EMAIL` | No | Sign up first, then set this to promote that user to `ADMIN` at startup |
| `FEE_INCOME_ACCOUNT` | No | Account number that receives transfer fees. **Fees are only charged when this is set.** |
| `PAYSTACK_SECRET_KEY` | No | Paystack secret key. **Card deposits are off until this is set.** Use a test key (`sk_test_...`) until you go live, and never commit it. |
| `PAYSTACK_CALLBACK_URL` | No | Where Paystack sends the customer after paying: your frontend's address (default `http://localhost:5173`) |
| `PAYSTACK_BASE_URL` | No | Defaults to `https://api.paystack.co`. Only change it to point at the local stand-in |
| `kyc.face.provider` | No | Face-verification provider. Unset by default (the KYC endpoint returns 503). `stub` is for local development only. |

Limits and fee tiers live in `application.properties` (`bank.limits.*`, `bank.fees.*`). Unverified users use the stricter `bank.limits.tier0-*` values. The default values are placeholders. Set real ones before going live.

## Frontend

`frontend/` is a React 19 + Vite web app (sign-in, dashboard, transfers, beneficiaries, notifications, statements, history, accounts, security and identity verification), wired to this API.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

In development Vite forwards `/api` to the backend at `http://localhost:8080` (set `BACKEND_URL` to change it), so there is nothing to configure and no CORS to worry about. To call a deployed API directly, set `VITE_API_BASE_URL` (see `frontend/.env.example`) and add the site's address to `CORS_ALLOWED_ORIGINS` on the backend.

Notes:
- Tokens are kept in memory only. Refreshing the page signs you out, and an expired access token is refreshed automatically.
- **Working with real data:** sign-up, sign-in, dashboard, add money by card (Paystack), send money (PIN, fee, limits, receipt), beneficiaries, notifications, history, statements (preview and CSV), accounts (open and close), PIN setup and password change, limits, identity verification, **savings goals** and **financial insights**.
- **Not built yet** (shown as "coming soon" for real accounts): cards (needs a card issuer), bill payments and airtime (needs a provider such as VTpass), scheduled and recurring payments, automatic savings, and support chat. **Forgotten-password reset** needs an email or SMS provider, so that page says it isn't available yet instead of pretending.
- For local testing of the identity check, start the backend with `KYC_FACE_PROVIDER=stub`. The stub approves any photo and must never be used in production.
- New accounts start with zero balance. To fund one while testing, make a user an admin (`ADMIN_EMAIL`) and call `POST /accounts/{id}/credit`.

## API Overview

All endpoints are under `/api/v1`. Everything except sign-up, `/auth/**` and the API docs needs the header `Authorization: Bearer <accessToken>`. Responses are wrapped as `{ "message": "...", "data": ... }`.

### Auth

| Method | Path | Description |
|---|---|---|
| POST | `/auth/login` | Email and password; returns access and refresh tokens |
| POST | `/auth/refresh` | Exchange a refresh token for a new pair (each refresh token works once) |
| POST | `/auth/logout` | Revoke a refresh token |

### Users

| Method | Path | Description |
|---|---|---|
| POST | `/users/signUp` | Create a user (public) |
| GET | `/users` | Returns only the caller |
| GET | `/users/id/{id}` | Your own profile |
| GET | `/users?email=` | Your own profile, by email |
| PUT | `/users/updateUser/{id}` | Update your profile (partial update) |
| DELETE | `/users/deleteUser/{id}` | Delete yourself (409 if you still own accounts) |
| POST | `/users/me/pin` | Set or change the transaction PIN (`{pin, password}`) |

### Accounts

| Method | Path | Description |
|---|---|---|
| GET | `/accounts` | Your accounts |
| GET | `/accounts/{id}` | One of your accounts |
| POST | `/accounts/user/{userId}` | Open an account (`userId` must be you) |
| GET | `/accounts/{id}/transactions?page=&size=` | Transaction history, newest first (max size 100) |
| GET | `/accounts/lookup/{accountNumber}` | Name enquiry |
| POST | `/accounts/transfer` | Transfer by account id |
| POST | `/accounts/transfer/by-number` | Transfer by 10-digit account number |
| PUT | `/accounts/{id}/user/{userId}` | Update account type or status |
| POST | `/accounts/{id}/user/{userId}/deactivate` | Deactivate an account |
| DELETE | `/accounts/{id}/user/{userId}` | Delete a zero-balance account |
| POST | `/accounts/{id}/credit` | **Admin only.** Add funds |
| POST | `/accounts/{id}/debit` | **Admin only.** Remove funds |

### Statements

| Method | Path | Description |
|---|---|---|
| GET | `/accounts/{id}/statement` | Query: `from`, `to` (`yyyy-MM-dd`, inclusive, default last 30 days), `type`, `format=json\|csv`. Max range 366 days. Includes opening and closing balances and a running balance per line. |

### Beneficiaries

| Method | Path | Description |
|---|---|---|
| GET | `/beneficiaries` | Your saved recipients |
| POST | `/beneficiaries` | Save a recipient: `{accountNumber, nickname?}` (the name is looked up for you) |
| DELETE | `/beneficiaries/{id}` | Remove one |

### Notifications

| Method | Path | Description |
|---|---|---|
| GET | `/notifications?unread=&page=&size=` | Your notifications, newest first |
| GET | `/notifications/unread-count` | `{ "unread": n }` |
| POST | `/notifications/{id}/read` | Mark one as read |
| POST | `/notifications/read-all` | Mark all as read |

Debit and credit alerts are created after each transfer or admin funding. They are best effort: a notification failure never fails or repeats a transfer.

### Savings goals

| Method | Path | Description |
|---|---|---|
| GET | `/savings` | Your active goals and the total saved |
| POST | `/savings` | Create a goal: `{name, type: FLEXIBLE or LOCKED, targetAmount, deadline?}` (a locked goal needs a future `deadline`) |
| POST | `/savings/{id}/deposit` | Move money from an account into the goal: `{accountId, amount}` (min ₦100) |
| POST | `/savings/{id}/withdraw` | Move money back (needs `X-Transaction-Pin`; refused while a locked goal is locked) |
| POST | `/savings/{id}/close` | Return what is saved and close the goal (needs `X-Transaction-Pin`) |
| PUT | `/savings/{id}/target` | Change the target |

Saved money leaves the account's balance (a `SAVINGS_OUT` ledger entry) and returns on withdrawal (`SAVINGS_IN`), so balances and statements always add up. Moves to and from your own savings are not counted as spending or income on the dashboard or insights.

### Insights and password

| Method | Path | Description |
|---|---|---|
| GET | `/insights?accountId=` | Six months of money in and out, this month's breakdown by type, the biggest payment, and how much went to savings |
| POST | `/users/me/password` | `{currentPassword, newPassword}`. Revokes every other session and returns a fresh token pair for this one |

### Deposits

| Method | Path | Description |
|---|---|---|
| POST | `/deposits` | Start a card payment: `{accountId, amount}`. Returns `{reference, authorizationUrl}`; send the customer to that URL |
| GET | `/deposits/{reference}` | Status (`PENDING`, `SUCCESS`, `FAILED`). If still pending it asks Paystack, so it also works when webhooks cannot reach you |
| POST | `/webhooks/paystack` | Called by Paystack, not by your app. Public, protected by the `x-paystack-signature` header |

### KYC

| Method | Path | Description |
|---|---|---|
| POST | `/kyc/face` | Upload a selfie (`multipart/form-data`, field `selfie`, JPEG or PNG, max 5 MB) |
| GET | `/kyc/status` | Your KYC tier and the latest face-check result |

### Example: a transfer

```bash
curl -X POST http://localhost:8080/api/v1/accounts/transfer/by-number \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Transaction-Pin: 1234" \
  -H "Idempotency-Key: 7c1f2a52-3b8e-4c0e-9b7a-1d2f3a4b5c6d" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId": 1, "toAccountNumber": "1234567890", "amount": 1500.00, "description": "Rent"}'
```

Amounts must be at least `0.01` and have at most 2 decimal places.

### Common status codes

| Code | Meaning |
|---|---|
| 400 | Validation error, or no PIN set yet |
| 413 / 415 | Selfie too large, or not a JPEG/PNG |
| 401 | Missing or invalid token |
| 403 | Not your resource, wrong PIN, or admin required |
| 404 | Not found (also returned for other people's accounts) |
| 409 | Conflict: insufficient funds, inactive account, duplicate data |
| 422 | Limit exceeded, or `Idempotency-Key` reused with a different request |
| 423 | Transaction PIN locked after 5 wrong attempts (15 minutes) |
| 503 | Face verification is not configured |

## Security Model

- Passwords and PINs are hashed with BCrypt. Refresh tokens are stored only as SHA-256 hashes.
- The caller's identity always comes from the JWT, never from the request URL or body.
- Users can only read or change their own accounts and profile. Accessing someone else's account returns 404.
- Transfers need the transaction PIN. Five wrong attempts lock it for 15 minutes.
- Direct credit and debit are admin-only.
- CSRF is disabled because the API is stateless and token-based.
- **Sign-in protection:** after 5 wrong passwords for one email or phone number, sign-in is refused for 15 minutes (even with the right password). Failures are stored hashed, tracked the same way for accounts that don't exist (so the response never reveals whether an account is real), and every sign-in does one password check regardless, so timing doesn't reveal it either. Separately, one address can make at most 60 sign-in/sign-up requests a minute (`bank.security.auth-requests-per-minute`); that limit is in memory, so in production also rate-limit at your proxy.
- **Closing an account** keeps it and its full history (status `CLOSED`). It needs a zero balance, disappears from the owner's lists, and can no longer send or receive.

## Card Deposits (Paystack)

Users can add money with a card. The customer pays on Paystack's own page, so card details never touch this server.

**How it works**
1. `POST /deposits` creates a `PENDING` deposit and asks Paystack to start a payment (the amount is sent in kobo).
2. The customer is sent to Paystack and pays.
3. Paystack calls `POST /webhooks/paystack` with a signed `charge.success` event. The server checks the signature, then the amount and currency against what it asked for, and only then credits the account.
4. When the customer returns to the app, it also asks `GET /deposits/{reference}`, which verifies with Paystack. Whichever of the two arrives first credits the account and the other does nothing.

**Safety rules built in**
- Money is never credited because a browser says so. Only a validly signed webhook or a server-to-server verify can credit it.
- The signature is HMAC-SHA512 over the exact raw request body, compared in constant time. Missing, wrong or tampered signatures get 401.
- Each deposit is credited **once**, however many webhooks, retries or page reloads happen: the deposit row is locked while settling, and the ledger entry has a unique `DEP-<reference>`.
- A paid amount or currency that differs from the request is **not** credited. The deposit is marked `FAILED` and an `ERROR` line (`DEPOSIT MISMATCH`) is logged so a person can follow it up with Paystack.
- No secret key means deposits are switched off (503) and the webhook refuses everything.
- Limits: at least ₦100; at most ₦1,000,000 per deposit, or ₦50,000 until the user is identity-verified (`bank.deposits.*`). Fees charged by Paystack are paid by you, not added to the customer's amount.

**Going live with Paystack**
1. Create a Paystack account and copy your **test** secret key from *Settings → API Keys & Webhooks*.
2. Set `PAYSTACK_SECRET_KEY=sk_test_...` (and `PAYSTACK_CALLBACK_URL` to your frontend's public address) for the backend.
3. In the same Paystack settings page, set the **Test Webhook URL** to `https://<your-api-host>/api/v1/webhooks/paystack`. Paystack cannot reach `localhost`; to try webhooks from your laptop, use a tunnel such as ngrok. Without a webhook, deposits still complete when the customer returns to the app (step 4 above).
4. Pay with one of Paystack's test cards, then switch to your live key only after you are happy.

This was written from Paystack's public API documentation and tested against a local stand-in, **not** against the real Paystack. Do a test-mode payment end to end before relying on it.

**Trying it without a Paystack account**

`tools/fake-paystack/server.js` is a local stand-in (Node only, nothing to install) with a fake checkout page:

```bash
node tools/fake-paystack/server.js
PAYSTACK_SECRET_KEY=sk_test_fake_dev_secret PAYSTACK_BASE_URL=http://localhost:9099 mvn spring-boot:run
```

It is for development only and proves nothing about the real Paystack. Never point production at it.

## Trying It in Swagger

1. Start the app and open `http://localhost:8080/swagger-ui/index.html`.
2. **Sign up:** `POST /api/v1/auth/signup` with `{firstName, lastName, email, phoneNumber, password, termsAccepted}`. The password needs 12 to 64 characters with upper case, lower case, a digit and a symbol. A first savings account is created for you.
3. **Log in:** `POST /api/v1/auth/login` with `{identifier, password}` (the identifier is your email or phone number), then copy the `accessToken`.
4. Click **Authorize** (top right), paste the token **without** the word `Bearer`, and confirm.
5. Open an account with `POST /accounts/user/{userId}` (your own id), and set a PIN with `POST /users/me/pin`.
6. To fund an account you need an admin (`ADMIN_EMAIL`). Log in as the admin, authorize with that token, and call `POST /accounts/{id}/credit`.
7. For transfers, fill in the `X-Transaction-Pin` field and, optionally, `Idempotency-Key` (any unique text). Reusing the same key and body returns the first result instead of sending money again.

Access tokens last 15 minutes. After that, log in again or use `/auth/refresh`.

## KYC and Face Verification

Users start at **tier 0** (unverified) with stricter transfer limits. A successful face check moves them to **tier 1**, which uses the standard limits.

- Face checks go through the `FaceVerificationProvider` interface (`src/main/java/dan/bank/bank/Kyc`). A real vendor adapter (for example Smile ID, Youverify, Dojah or AWS Rekognition) implements it, so the app doesn't depend on one vendor.
- **Only the verdict is stored** (status, provider name and reference). Selfie images are never saved or logged.
- Uploads are accepted only if the file really is a JPEG or PNG (checked by its first bytes, not the client-supplied type).
- **No real provider is bundled.** With `kyc.face.provider` unset, `POST /kyc/face` returns 503. `kyc.face.provider=stub` approves any image **without checking a face** and must never be enabled in production.
- Real liveness detection (to resist photo spoofing) has to come from the vendor's SDK in the client app.

## How Transfers Work

1. The caller must own the source account and supply the correct PIN.
2. The fee is calculated, and the accounts are locked.
   The source, destination and fee accounts are locked in ascending id order, which prevents deadlocks.
3. The per-transfer and daily limits for the sender's KYC tier are checked.
4. The balance must cover the amount plus the fee.
5. Balances update and ledger rows are written in one database transaction:
   `TRANSFER_OUT`, `TRANSFER_IN`, and, if a fee applies, two `FEE` rows. Every row has its own unique reference sharing a transfer-id prefix.
6. If an `Idempotency-Key` was sent, the result is stored against it, so a retry returns the original result.

## Project Structure

```
src/main/java/dan/bank/bank
├── account/       accounts, transfers, limits and fees, idempotency, statements
├── auth/          JWT filter, token service, refresh tokens, current-user helper
├── beneficiary/   saved recipients
├── kyc/           face verification port, KYC tiers, selfie upload
├── notification/  in-app notifications and transfer alerts
├── user/          users, roles, transaction PIN
├── SecurityConfig.java, OpenApiConfig.java, ApiExceptionHandler.java
└── AdminBootstrap.java
src/main/resources/db/migration   Flyway migrations (V1 to V6)
src/test                          unit and integration tests (H2)
```

## Testing

```bash
mvn test
```

Tests run against an in-memory H2 database in PostgreSQL mode, with the real Flyway migrations. They cover authorization, PIN lockout, idempotency, limits, fees and concurrent transfers.

## Roadmap

- A real face-verification adapter (Smile ID, Youverify or Dojah) and ID-document checks for higher KYC tiers
- Withdrawals and transfers to other banks (payouts)
- Email and SMS delivery for notifications, and PDF statements
- Bill payments (airtime, data, electricity)
- Double-entry ledger and audit log
- CI pipeline, and frontend tests (the app is currently checked by end-to-end browser runs, not committed tests)

## License

No license has been chosen yet. Add a `LICENSE` file before sharing the code publicly.
