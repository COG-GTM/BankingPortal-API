import { getCountries, isValidPhoneNumber as isValidPhone, type CountryCode } from 'libphonenumber-js/max';
import type { AccountResponse, TransactionDTO, TransactionType, UserResponse } from '@/api/types';
import { EXTERNAL_ACCOUNT, MOCK_OTP, SEED_USERS, janeHistory, type SeedUser } from './fixtures';
import { ApiMessages, format } from './messages';

/** Thrown by mock operations; handlers turn it into a response with the same status/body as GlobalExceptionHandler. */
export class MockHttpError extends Error {
  constructor(
    readonly status: number,
    readonly body: string,
  ) {
    super(body);
  }
}

const badRequest = (msg: string) => new MockHttpError(400, msg);
const unauthorized = (msg: string) => new MockHttpError(401, msg);
const notFound = (msg: string) => new MockHttpError(404, msg);
const serverError = (msg: string) => new MockHttpError(500, `{"message": "An unexpected error occurred: ${msg}"}`);

export interface MockAccount {
  accountNumber: string;
  accountType: string;
  branch: string;
  ifscCode: string;
  balance: number;
  pin: string | null;
}

export interface MockUser {
  name: string;
  email: string;
  password: string;
  countryCode: string;
  phoneNumber: string;
  address: string;
  account: MockAccount;
}

/** Each row belongs to one account's history (`owner`). */
interface MockTxn extends TransactionDTO {
  owner: string;
}

interface MockState {
  users: MockUser[];
  transactions: MockTxn[];
  nextTxnId: number;
  /** `@Cacheable("idempotency")` stand-in: key → cached success body. */
  idempotency: Map<string, string>;
  revokedTokens: Set<string>;
  resetTokens: Map<string, string>;
  otpAttempts: Map<string, { count: number; limitReachedAt: number | null }>;
}

const DAY = 86_400_000;
const OTP_ATTEMPTS_LIMIT = 3;
const OTP_RESET_WAITING_TIME_MINUTES = 10;

let state: MockState = createState();

function toUser(seed: SeedUser): MockUser {
  return {
    name: seed.name,
    email: seed.email,
    password: seed.password,
    countryCode: seed.countryCode,
    phoneNumber: seed.phoneNumber,
    address: seed.address,
    account: {
      accountNumber: seed.accountNumber,
      accountType: 'Savings',
      branch: 'NIT',
      ifscCode: 'NIT001',
      balance: seed.balance,
      pin: seed.pin,
    },
  };
}

function createState(now = Date.now()): MockState {
  const jane = SEED_USERS.jane.accountNumber;
  const john = SEED_USERS.john.accountNumber;
  const rows: Omit<MockTxn, 'id'>[] = [];
  for (const t of janeHistory()) {
    const day = new Date(now - t.daysAgo * DAY);
    day.setHours(t.hour, (t.amount / 100) % 60, 0, 0);
    const transactionDate = day.getTime();
    const base = { amount: t.amount, transactionDate };
    if (t.type === 'CASH_TRANSFER') {
      const target = t.counterparty ?? john;
      rows.push({ ...base, transactionType: 'CASH_TRANSFER', sourceAccountNumber: jane, targetAccountNumber: target, owner: jane });
      rows.push({ ...base, transactionType: 'CASH_CREDIT', sourceAccountNumber: jane, targetAccountNumber: target, owner: target });
    } else if (t.type === 'CASH_CREDIT') {
      const source = t.counterparty ?? EXTERNAL_ACCOUNT;
      rows.push({ ...base, transactionType: 'CASH_CREDIT', sourceAccountNumber: source, targetAccountNumber: jane, owner: jane });
    } else {
      rows.push({ ...base, transactionType: t.type, sourceAccountNumber: jane, targetAccountNumber: 'N/A', owner: jane });
    }
  }
  rows.sort((a, b) => a.transactionDate - b.transactionDate);
  const transactions = rows.map((r, i) => ({ ...r, id: i + 1 }));

  return {
    users: [toUser(SEED_USERS.jane), toUser(SEED_USERS.john)],
    transactions,
    nextTxnId: transactions.length + 1,
    idempotency: new Map(),
    revokedTokens: new Set(),
    resetTokens: new Map(),
    otpAttempts: new Map(),
  };
}

/** Restore the seeded fixtures (balances, PINs, history) and clear every cache. */
export function resetMockDb(now?: number) {
  state = createState(now);
}

// ---------------------------------------------------------------------------
// Lookups / mappers
// ---------------------------------------------------------------------------

export function findByAccountNumber(accountNumber: string | null | undefined): MockUser | undefined {
  return state.users.find((u) => u.account.accountNumber === accountNumber);
}

function findByEmail(email: string | null | undefined): MockUser | undefined {
  if (!email) return undefined;
  return state.users.find((u) => u.email.toLowerCase() === email.toLowerCase());
}

/** UserServiceImpl#getUserByIdentifier: email first, then account number. */
export function getUserByIdentifier(identifier: unknown): MockUser {
  const id = identifier == null ? 'null' : String(identifier);
  const user = findByEmail(id) ?? findByAccountNumber(id);
  if (!user) throw badRequest(format(ApiMessages.USER_NOT_FOUND_BY_IDENTIFIER, id));
  return user;
}

export function toUserResponse(u: MockUser): UserResponse {
  return {
    name: u.name,
    email: u.email,
    countryCode: u.countryCode,
    phoneNumber: u.phoneNumber,
    address: u.address,
    accountNumber: u.account.accountNumber,
    ifscCode: u.account.ifscCode,
    branch: u.account.branch,
    accountType: u.account.accountType,
  };
}

export function toAccountResponse(u: MockUser): AccountResponse {
  const { accountNumber, balance, accountType, branch, ifscCode } = u.account;
  return { accountNumber, balance, accountType, branch, ifscCode };
}

/** TransactionServiceImpl: this account's rows, newest first. */
export function listTransactions(accountNumber: string): TransactionDTO[] {
  return state.transactions
    .filter((t) => t.owner === accountNumber)
    .sort((a, b) => b.transactionDate - a.transactionDate || b.id - a.id)
    .map(({ owner: _owner, ...dto }) => dto);
}

// ---------------------------------------------------------------------------
// Tokens (mock JWT: decodable so a page reload keeps the session)
// ---------------------------------------------------------------------------

const b64 = (s: string) => btoa(s).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
const unb64 = (s: string) => atob(s.replace(/-/g, '+').replace(/_/g, '/'));

export function issueToken(accountNumber: string): string {
  const header = b64(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = b64(JSON.stringify({ sub: accountNumber, iat: Date.now(), jti: crypto.randomUUID() }));
  return `${header}.${payload}.mock`;
}

/** TokenServiceImpl#validateToken equivalent. Returns the account number (JWT subject). */
export function verifyToken(token: string): string {
  if (state.revokedTokens.has(token)) throw unauthorized(ApiMessages.TOKEN_NOT_FOUND_ERROR);
  const parts = token.split('.');
  if (parts.length !== 3 || parts[2] !== 'mock') throw unauthorized(ApiMessages.TOKEN_INVALID_ERROR);
  try {
    const { sub } = JSON.parse(unb64(parts[1] as string)) as { sub?: string };
    if (sub && findByAccountNumber(sub)) return sub;
  } catch {
    // fall through
  }
  throw unauthorized(ApiMessages.TOKEN_INVALID_ERROR);
}

export function revokeToken(token: string) {
  state.revokedTokens.add(token);
}

// ---------------------------------------------------------------------------
// Idempotency (AccountController @Cacheable keys)
// ---------------------------------------------------------------------------

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value) ?? 'null';
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  const rec = value as Record<string, unknown>;
  return `{${Object.keys(rec)
    .sort()
    .map((k) => `${JSON.stringify(k)}:${stableStringify(rec[k])}`)
    .join(',')}}`;
}

export interface IdempotentResult {
  body: string;
  replayed: boolean;
}

/**
 * Key = account + ':' + endpoint + ':' + request hash. A cached success is returned without running
 * `fn` (no state change). Failures are not cached, matching Spring's @Cacheable.
 */
export function idempotent(accountNumber: string, endpoint: string, body: unknown, fn: () => string): IdempotentResult {
  const key = `${accountNumber}:${endpoint}:${stableStringify(body)}`;
  const cached = state.idempotency.get(key);
  if (cached !== undefined) return { body: cached, replayed: true };
  const result = fn();
  state.idempotency.set(key, result);
  return { body: result, replayed: false };
}

// ---------------------------------------------------------------------------
// Account rules (AccountServiceImpl)
// ---------------------------------------------------------------------------

function account(accountNumber: string): MockAccount {
  const user = findByAccountNumber(accountNumber);
  if (!user) throw notFound(ApiMessages.ACCOUNT_NOT_FOUND);
  return user.account;
}

function validatePin(accountNumber: string, pin: string | null) {
  const acc = account(accountNumber);
  if (acc.pin == null) throw unauthorized(ApiMessages.PIN_NOT_CREATED);
  if (pin == null || pin === '') throw unauthorized(ApiMessages.PIN_EMPTY_ERROR);
  if (pin !== acc.pin) throw unauthorized(ApiMessages.PIN_INVALID_ERROR);
}

function validatePassword(accountNumber: string, password: string | null) {
  const user = findByAccountNumber(accountNumber);
  if (!user) throw notFound(ApiMessages.ACCOUNT_NOT_FOUND);
  if (password == null || password === '') throw unauthorized(ApiMessages.PASSWORD_EMPTY_ERROR);
  if (password !== user.password) throw unauthorized(ApiMessages.PASSWORD_INVALID_ERROR);
}

function validateNewPin(pin: string | null) {
  if (pin == null || pin === '') throw badRequest(ApiMessages.PIN_EMPTY_ERROR);
  if (!/^[0-9]{4}$/.test(pin)) throw badRequest(ApiMessages.PIN_FORMAT_INVALID_ERROR);
}

export function validateAmount(amount: number) {
  if (amount <= 0) throw badRequest(ApiMessages.AMOUNT_NEGATIVE_ERROR);
  if (amount % 100 !== 0) throw badRequest(ApiMessages.AMOUNT_NOT_MULTIPLE_OF_100_ERROR);
  if (amount > 100_000) throw badRequest(ApiMessages.AMOUNT_EXCEED_100_000_ERROR);
}

function addTxn(row: Omit<MockTxn, 'id' | 'transactionDate'>) {
  state.transactions.push({ ...row, id: state.nextTxnId++, transactionDate: Date.now() });
}

export function isPinCreated(accountNumber: string): boolean {
  return account(accountNumber).pin != null;
}

export function createPin(accountNumber: string, password: string | null, pin: string | null) {
  validatePassword(accountNumber, password);
  const acc = account(accountNumber);
  if (acc.pin != null) throw unauthorized(ApiMessages.PIN_ALREADY_EXISTS);
  validateNewPin(pin);
  acc.pin = pin;
}

export function updatePin(accountNumber: string, oldPin: string | null, password: string | null, newPin: string | null) {
  validatePassword(accountNumber, password);
  validatePin(accountNumber, oldPin);
  validateNewPin(newPin);
  account(accountNumber).pin = newPin;
}

export function cashDeposit(accountNumber: string, pin: string | null, amount: number) {
  validatePin(accountNumber, pin);
  validateAmount(amount);
  account(accountNumber).balance += amount;
  addTxn({ amount, transactionType: 'CASH_DEPOSIT', sourceAccountNumber: accountNumber, targetAccountNumber: 'N/A', owner: accountNumber });
}

export function cashWithdrawal(accountNumber: string, pin: string | null, amount: number) {
  validatePin(accountNumber, pin);
  validateAmount(amount);
  const acc = account(accountNumber);
  if (acc.balance < amount) throw badRequest(ApiMessages.BALANCE_INSUFFICIENT_ERROR);
  acc.balance -= amount;
  addTxn({ amount, transactionType: 'CASH_WITHDRAWAL', sourceAccountNumber: accountNumber, targetAccountNumber: 'N/A', owner: accountNumber });
}

export function fundTransfer(source: string, target: string | null, pin: string | null, amount: number) {
  validatePin(source, pin);
  validateAmount(amount);
  if (source === target) throw badRequest(ApiMessages.CASH_TRANSFER_SAME_ACCOUNT_ERROR);
  const targetUser = findByAccountNumber(target);
  if (!targetUser) throw notFound(ApiMessages.ACCOUNT_NOT_FOUND);
  const src = account(source);
  if (src.balance < amount) throw badRequest(ApiMessages.BALANCE_INSUFFICIENT_ERROR);
  src.balance -= amount;
  targetUser.account.balance += amount;
  const t = targetUser.account.accountNumber;
  const type: TransactionType = 'CASH_TRANSFER';
  addTxn({ amount, transactionType: type, sourceAccountNumber: source, targetAccountNumber: t, owner: source });
  addTxn({ amount, transactionType: 'CASH_CREDIT', sourceAccountNumber: source, targetAccountNumber: t, owner: t });
}

// ---------------------------------------------------------------------------
// Users (UserServiceImpl + ValidationUtil)
// ---------------------------------------------------------------------------

export interface UserInput {
  name?: string | null;
  email?: string | null;
  password?: string | null;
  countryCode?: string | null;
  phoneNumber?: string | null;
  address?: string | null;
}

/** libphonenumber's supported regions, same source as ValidationUtil#isValidCountryCode. */
const REGIONS = new Set<string>(getCountries());

/** ValidationUtil#isValidPhoneNumber equivalent (libphonenumber metadata). */
function isValidPhoneNumber(phone: string, region: string): boolean {
  try {
    return isValidPhone(phone, region as CountryCode);
  } catch {
    return false;
  }
}

/** ValidationUtil#validatePassword, including the "one uppercase letter, ... and one digit" message. */
export function validatePasswordRules(password: string) {
  if (password.length < 8) throw badRequest(ApiMessages.PASSWORD_TOO_SHORT_ERROR);
  if (password.length >= 128) throw badRequest(ApiMessages.PASSWORD_TOO_LONG_ERROR);
  if (/\s/.test(password)) throw badRequest(ApiMessages.PASSWORD_CONTAINS_WHITESPACE_ERROR);
  const missing: string[] = [];
  if (!/[A-Z]/.test(password)) missing.push('one uppercase letter');
  if (!/[a-z]/.test(password)) missing.push('one lowercase letter');
  if (!/[0-9]/.test(password)) missing.push('one digit');
  if (!/[^A-Za-z0-9]/.test(password)) missing.push('one special character');
  if (missing.length) {
    let msg = `Password must contain at least ${missing.join(', ')}`;
    const last = msg.lastIndexOf(',');
    if (last > -1) msg = `${msg.slice(0, last)} and${msg.slice(last + 1)}`;
    throw badRequest(msg);
  }
}

function validateUserDetails(u: UserInput): asserts u is Required<{ [K in keyof UserInput]: string }> {
  const empty = (v: unknown) => v == null || v === '';
  if (empty(u.name)) throw badRequest(ApiMessages.USER_NAME_EMPTY_ERROR);
  if (empty(u.address)) throw badRequest(ApiMessages.USER_ADDRESS_EMPTY_ERROR);
  if (empty(u.email)) throw badRequest(ApiMessages.USER_EMAIL_EMPTY_ERROR);
  if (empty(u.countryCode)) throw badRequest(ApiMessages.USER_COUNTRY_CODE_EMPTY_ERROR);
  if (empty(u.phoneNumber)) throw badRequest(ApiMessages.USER_PHONE_NUMBER_EMPTY_ERROR);
  if (empty(u.password)) throw badRequest(ApiMessages.PASSWORD_EMPTY_ERROR);
  const email = String(u.email);
  if (!/^[^\s@]+@[^\s@]+$/.test(email)) throw badRequest(format(ApiMessages.USER_EMAIL_ADDRESS_INVALID_ERROR, email));
  const cc = String(u.countryCode);
  if (!REGIONS.has(cc)) throw badRequest(format(ApiMessages.USER_COUNTRY_CODE_INVALID_ERROR, cc));
  const phone = String(u.phoneNumber);
  if (!isValidPhoneNumber(phone, cc)) throw badRequest(format(ApiMessages.USER_PHONE_NUMBER_INVALID_ERROR, phone, cc));
  validatePasswordRules(String(u.password));
}

function newAccountNumber(): string {
  let n: string;
  do {
    n = crypto.randomUUID().replace(/-/g, '').slice(0, 6);
  } while (findByAccountNumber(n));
  return n;
}

export function registerUser(input: UserInput | null): MockUser {
  if (!input) throw badRequest(ApiMessages.USER_DETAILS_EMPTY_ERROR);
  validateUserDetails(input);
  if (findByEmail(input.email)) throw badRequest(ApiMessages.USER_EMAIL_ALREADY_EXISTS_ERROR);
  if (state.users.some((u) => u.phoneNumber === input.phoneNumber)) {
    throw badRequest(ApiMessages.USER_PHONE_NUMBER_ALREADY_EXISTS_ERROR);
  }
  const user: MockUser = {
    name: input.name,
    email: input.email,
    password: input.password,
    countryCode: input.countryCode.toUpperCase(),
    phoneNumber: input.phoneNumber,
    address: input.address,
    account: { accountNumber: newAccountNumber(), accountType: 'Savings', branch: 'NIT', ifscCode: 'NIT001', balance: 0, pin: null },
  };
  state.users.push(user);
  return user;
}

/** Password login. Unknown identifier → 400, wrong password → 401 "Bad credentials". */
export function authenticate(identifier: unknown, password: unknown): MockUser {
  const user = getUserByIdentifier(identifier);
  if (password !== user.password) throw unauthorized(ApiMessages.BAD_CREDENTIALS);
  return user;
}

/** UserServiceImpl#updateUser: authenticates with the current password, which is kept unchanged. */
export function updateUser(accountNumber: string, input: UserInput | null): MockUser {
  const user = findByAccountNumber(accountNumber);
  if (!user || !input || input.password !== user.password) throw unauthorized(ApiMessages.BAD_CREDENTIALS);
  validateUserDetails(input);
  const clash = findByEmail(input.email);
  if (clash && clash !== user) throw serverError(`Duplicate entry '${input.email}' for key 'user.email'`);
  const phoneClash = state.users.find((u) => u.phoneNumber === input.phoneNumber);
  if (phoneClash && phoneClash !== user) {
    throw serverError(`Duplicate entry '${input.phoneNumber}' for key 'user.phone_number'`);
  }
  Object.assign(user, {
    name: input.name,
    email: input.email,
    countryCode: input.countryCode,
    phoneNumber: input.phoneNumber,
    address: input.address,
  });
  return user;
}

// ---------------------------------------------------------------------------
// OTP + password reset (OtpServiceImpl / AuthServiceImpl)
// ---------------------------------------------------------------------------

/** Three sends per account, then "OTP generation limit exceeded" until the 10-minute wait has passed. */
export function generateOtp(identifier: unknown, now = Date.now()): MockUser {
  const user = getUserByIdentifier(identifier);
  const acc = user.account.accountNumber;
  const entry = state.otpAttempts.get(acc) ?? { count: 0, limitReachedAt: null };
  if (entry.count >= OTP_ATTEMPTS_LIMIT) {
    const waitMs = OTP_RESET_WAITING_TIME_MINUTES * 60_000;
    if (entry.limitReachedAt != null && now - entry.limitReachedAt > waitMs) {
      entry.count = 0;
      entry.limitReachedAt = null;
    } else {
      throw badRequest(format(ApiMessages.OTP_GENERATION_LIMIT_EXCEEDED, OTP_RESET_WAITING_TIME_MINUTES));
    }
  }
  entry.count += 1;
  if (entry.count >= OTP_ATTEMPTS_LIMIT) entry.limitReachedAt = now;
  state.otpAttempts.set(acc, entry);
  console.info(`[mock] OTP for ${user.email} (${acc}) is ${MOCK_OTP} — any 6 digits are accepted.`);
  return user;
}

function validateOtpRequest(identifier: unknown, otp: unknown) {
  if (identifier == null || identifier === '') throw badRequest(ApiMessages.IDENTIFIER_MISSING_ERROR);
  if (otp == null || otp === '') throw badRequest(ApiMessages.OTP_MISSING_ERROR);
}

/** Any 6-digit OTP is accepted in mock mode; anything else → 401 "Invalid OTP". */
export function verifyOtp(identifier: unknown, otp: unknown): MockUser {
  validateOtpRequest(identifier, otp);
  const user = getUserByIdentifier(identifier);
  if (!/^\d{6}$/.test(String(otp))) throw unauthorized(ApiMessages.OTP_INVALID_ERROR);
  return user;
}

export function issueResetToken(user: MockUser): string {
  const token = crypto.randomUUID();
  state.resetTokens.set(token, user.account.accountNumber);
  return token;
}

/** Reset tokens are single-use. The backend does not re-validate password strength here. */
export function resetPassword(identifier: unknown, resetToken: unknown, newPassword: unknown) {
  const user = getUserByIdentifier(identifier);
  const token = String(resetToken ?? '');
  const owner = state.resetTokens.get(token);
  state.resetTokens.delete(token);
  if (owner !== user.account.accountNumber) throw unauthorized(ApiMessages.TOKEN_INVALID_ERROR);
  user.password = String(newPassword ?? '');
}

/** Test/debug helper. */
export function getMockState(): Readonly<MockState> {
  return state;
}
