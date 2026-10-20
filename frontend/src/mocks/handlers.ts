import { delay, http, HttpResponse } from 'msw';
import * as db from './db';
import { ApiMessages, format } from './messages';

const LATENCY_MS = import.meta.env.MODE === 'test' ? 0 : 200;

type Json = Record<string, unknown>;

function text(body: string, status = 200, headers: Record<string, string> = {}) {
  return new HttpResponse(body, {
    status,
    headers: { 'Content-Type': 'text/plain;charset=UTF-8', ...headers },
  });
}

/** Spring's /error JSON shape, used when JwtAuthenticationFilter calls response.sendError(401, msg). */
function springError(status: number, message: string, path: string) {
  return HttpResponse.json(
    { timestamp: new Date().toISOString(), status, error: 'Unauthorized', message, path },
    { status },
  );
}

async function readJson(request: Request): Promise<Json> {
  const raw = await request.text();
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    return parsed && typeof parsed === 'object' ? (parsed as Json) : {};
  } catch (e) {
    throw new db.MockHttpError(500, `{"message": "An unexpected error occurred: JSON parse error: ${(e as Error).message}"}`);
  }
}

const str = (v: unknown): string | null => (v == null ? null : String(v));

/** Jackson coerces "100" → 100.0, a missing field → 0.0, and fails on non-numeric strings. */
function amountOf(body: Json): number {
  const v = body.amount ?? 0;
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) {
    throw new db.MockHttpError(
      500,
      `{"message": "An unexpected error occurred: JSON parse error: Cannot deserialize value of type \`double\` from String \\"${String(v)}\\""}`,
    );
  }
  return n;
}

interface Ctx {
  request: Request;
  body: Json;
}

interface AuthedCtx extends Ctx {
  accountNumber: string;
  token: string;
}

function authenticate(request: Request): { accountNumber: string; token: string } | Response {
  const header = request.headers.get('Authorization');
  const path = new URL(request.url).pathname;
  if (!header) return text(ApiMessages.ACCESS_DENIED, 401);
  if (!header.startsWith('Bearer ')) return springError(401, "Token must start with 'Bearer '", path);
  const token = header.slice(7);
  try {
    return { accountNumber: db.verifyToken(token), token };
  } catch (e) {
    if (e instanceof db.MockHttpError) return springError(401, e.body, path);
    throw e;
  }
}

async function run(fn: () => Promise<Response> | Response): Promise<Response> {
  if (LATENCY_MS) await delay(LATENCY_MS);
  try {
    return await fn();
  } catch (e) {
    if (e instanceof db.MockHttpError) return text(e.body, e.status);
    throw e;
  }
}

const publicRoute = (fn: (ctx: Ctx) => Response | Promise<Response>) =>
  async ({ request }: { request: Request }) =>
    run(async () => fn({ request, body: request.method === 'GET' ? {} : await readJson(request) }));

const authedRoute = (fn: (ctx: AuthedCtx) => Response | Promise<Response>) =>
  async ({ request }: { request: Request }) =>
    run(async () => {
      const auth = authenticate(request);
      if (auth instanceof Response) return auth;
      const body = request.method === 'GET' ? {} : await readJson(request);
      return fn({ request, body, ...auth });
    });

/** Idempotent money/PIN endpoint: replays the cached success for an identical request. */
function idempotentRoute(endpoint: string, fn: (ctx: AuthedCtx) => string) {
  return authedRoute((ctx) => {
    const result = db.idempotent(ctx.accountNumber, endpoint, ctx.body, () => fn(ctx));
    return text(result.body, 200, result.replayed ? { 'X-Mock-Idempotent-Replay': 'true' } : {});
  });
}

const tokenBody = (accountNumber: string) => format(ApiMessages.TOKEN_ISSUED_SUCCESS, db.issueToken(accountNumber));

export const handlers = [
  // ---- /api/users ----
  http.post(
    '/api/users/register',
    publicRoute(({ body }) => text(JSON.stringify(db.toUserResponse(db.registerUser(body)))) ),
  ),
  http.post(
    '/api/users/login',
    publicRoute(({ body }) => {
      const user = db.authenticate(body.identifier, body.password);
      return text(tokenBody(user.account.accountNumber));
    }),
  ),
  http.post(
    '/api/users/generate-otp',
    publicRoute(({ body }) => text(format(ApiMessages.OTP_SENT_SUCCESS, db.generateOtp(body.identifier).email))),
  ),
  http.post(
    '/api/users/verify-otp',
    publicRoute(({ body }) => text(tokenBody(db.verifyOtp(body.identifier, body.otp).account.accountNumber))),
  ),
  http.post(
    '/api/users/update',
    authedRoute(({ accountNumber, body }) => text(JSON.stringify(db.toUserResponse(db.updateUser(accountNumber, body))))),
  ),
  http.get(
    '/api/users/logout',
    authedRoute(({ token }) => {
      db.revokeToken(token);
      return new HttpResponse(null, { status: 302, headers: { Location: '/logout' } });
    }),
  ),

  // ---- /api/auth/password-reset ----
  http.post(
    '/api/auth/password-reset/send-otp',
    publicRoute(({ body }) => text(format(ApiMessages.OTP_SENT_SUCCESS, db.generateOtp(body.identifier).email))),
  ),
  http.post(
    '/api/auth/password-reset/verify-otp',
    publicRoute(({ body }) => {
      const user = db.verifyOtp(body.identifier, body.otp);
      return text(format(ApiMessages.PASSWORD_RESET_TOKEN_ISSUED, db.issueResetToken(user)));
    }),
  ),
  http.post(
    '/api/auth/password-reset',
    publicRoute(({ body }) => {
      db.resetPassword(body.identifier, body.resetToken, body.newPassword);
      return text(ApiMessages.PASSWORD_RESET_SUCCESS);
    }),
  ),

  // ---- /api/dashboard ----
  http.get(
    '/api/dashboard/user',
    authedRoute(({ accountNumber }) => text(JSON.stringify(db.toUserResponse(db.findByAccountNumber(accountNumber)!)))),
  ),
  http.get(
    '/api/dashboard/account',
    authedRoute(({ accountNumber }) => text(JSON.stringify(db.toAccountResponse(db.findByAccountNumber(accountNumber)!)))),
  ),

  // ---- /api/account ----
  http.get(
    '/api/account/pin/check',
    authedRoute(({ accountNumber }) => text(db.isPinCreated(accountNumber) ? ApiMessages.PIN_CREATED : ApiMessages.PIN_NOT_CREATED)),
  ),
  http.post(
    '/api/account/pin/create',
    idempotentRoute('/api/account/pin/create', ({ accountNumber, body }) => {
      db.createPin(accountNumber, str(body.password), str(body.pin));
      return ApiMessages.PIN_CREATION_SUCCESS;
    }),
  ),
  http.post(
    '/api/account/pin/update',
    idempotentRoute('/api/account/pin/update', ({ accountNumber, body }) => {
      db.updatePin(accountNumber, str(body.oldPin), str(body.password), str(body.newPin));
      return ApiMessages.PIN_UPDATE_SUCCESS;
    }),
  ),
  http.post(
    '/api/account/deposit',
    idempotentRoute('/api/account/deposit', ({ accountNumber, body }) => {
      db.cashDeposit(accountNumber, str(body.pin), amountOf(body));
      return ApiMessages.CASH_DEPOSIT_SUCCESS;
    }),
  ),
  http.post(
    '/api/account/withdraw',
    idempotentRoute('/api/account/withdraw', ({ accountNumber, body }) => {
      db.cashWithdrawal(accountNumber, str(body.pin), amountOf(body));
      return ApiMessages.CASH_WITHDRAWAL_SUCCESS;
    }),
  ),
  http.post(
    '/api/account/fund-transfer',
    idempotentRoute('/api/account/fund-transfer', ({ accountNumber, body }) => {
      db.fundTransfer(accountNumber, str(body.targetAccountNumber), str(body.pin), amountOf(body));
      return ApiMessages.CASH_TRANSFER_SUCCESS;
    }),
  ),
  http.get(
    '/api/account/transactions',
    authedRoute(({ accountNumber }) => text(JSON.stringify(db.listTransactions(accountNumber)))),
  ),
  http.get(
    '/api/account/send-statement',
    authedRoute(({ accountNumber }) => {
      console.info(`[mock] Bank statement for ${accountNumber} "emailed" (${db.listTransactions(accountNumber).length} transactions).`);
      return text(ApiMessages.STATEMENT_SENT);
    }),
  ),
];
