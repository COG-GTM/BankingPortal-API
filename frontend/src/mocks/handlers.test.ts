import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '@/api/client';
import {
  checkPin,
  createPin,
  deposit,
  fundTransfer,
  generateOtp,
  getAccountDetails,
  getTransactions,
  getUserDetails,
  login,
  logout,
  verifyOtp,
  withdraw,
} from '@/api/endpoints';
import { tokenStore } from '@/api/token';
import { SEED_USERS } from './fixtures';
import { getMockState } from './db';

const jane = SEED_USERS.jane;
const john = SEED_USERS.john;

async function signInAs(user: { email: string; password: string }) {
  const { token } = await login({ identifier: user.email, password: user.password });
  tokenStore.set(token);
}

const balance = async () => (await getAccountDetails()).balance;
const err = (p: Promise<unknown>) => p.then(() => null, (e: unknown) => e as ApiError);

describe('seed data', () => {
  it('has two 6-char accounts, ~40 transactions over 90 days, and one user without a PIN', async () => {
    await signInAs(jane);
    const me = await getUserDetails();
    expect(me.accountNumber).toMatch(/^[0-9A-Z]{6}$/);
    expect(await balance()).toBe(48_200);
    const txns = await getTransactions();
    expect(txns).toHaveLength(40);
    const span = (txns[0]!.transactionDate - txns[txns.length - 1]!.transactionDate) / 86_400_000;
    expect(span).toBeGreaterThan(80);
    expect(span).toBeLessThanOrEqual(90);
    expect(await checkPin()).toBe(true);

    await signInAs(john);
    expect(await checkPin()).toBe(false);
  });

  it('logs in by account number and rejects a wrong password with 401 "Bad credentials"', async () => {
    await expect(login({ identifier: jane.accountNumber, password: jane.password })).resolves.toHaveProperty('token');
    expect(await err(login({ identifier: jane.email, password: 'nope' }))).toMatchObject({ status: 401, message: 'Bad credentials' });
  });
});

describe('amount rules', () => {
  beforeEach(() => signInAs(jane));

  it.each([
    [0, 'Amount must be greater than 0'],
    [-100, 'Amount must be greater than 0'],
    [150, 'Amount must be in multiples of 100'],
    [100_100, 'Amount cannot be greater than 100,000'],
  ])('rejects %d with 400 "%s"', async (amount, message) => {
    expect(await err(deposit({ pin: jane.pin, amount }))).toMatchObject({ status: 400, message });
    expect(await balance()).toBe(48_200);
  });

  it('accepts 100,000 exactly and returns the ApiMessages body', async () => {
    await expect(deposit({ pin: jane.pin, amount: 100_000 })).resolves.toEqual({ msg: 'Cash deposited successfully' });
    expect(await balance()).toBe(148_200);
  });
});

describe('account rules', () => {
  beforeEach(() => signInAs(jane));

  it('rejects withdrawals above the balance with 400 "Insufficient balance"', async () => {
    expect(await err(withdraw({ pin: jane.pin, amount: 48_300 }))).toMatchObject({ status: 400, message: 'Insufficient balance' });
    expect(await balance()).toBe(48_200);
  });

  it('rejects a wrong PIN with 401 "Invalid PIN" and keeps the session', async () => {
    expect(await err(withdraw({ pin: '9999', amount: 100 }))).toMatchObject({ status: 401, message: 'Invalid PIN' });
    expect(tokenStore.get()).not.toBeNull();
    expect(await balance()).toBe(48_200);
  });

  it('requires a PIN before moving money', async () => {
    await signInAs(john);
    expect(await err(deposit({ pin: '1234', amount: 100 }))).toMatchObject({
      status: 401,
      message: 'PIN has not been created for this account',
    });
    await expect(createPin({ pin: '4321', password: john.password })).resolves.toEqual({ hasPIN: true, msg: 'PIN created successfully' });
    await expect(deposit({ pin: '4321', amount: 100 })).resolves.toEqual({ msg: 'Cash deposited successfully' });
  });

  it('replays an identical request from the idempotency cache without mutating state', async () => {
    const body = { pin: jane.pin, amount: 500 };
    await withdraw(body);
    const before = await getTransactions();
    await expect(withdraw({ ...body })).resolves.toEqual({ msg: 'Cash withdrawn successfully' });
    expect(await balance()).toBe(47_700);
    expect(await getTransactions()).toHaveLength(before.length);

    await withdraw({ pin: jane.pin, amount: 600 });
    expect(await balance()).toBe(47_100);
  });

  it('transfer debits the source, credits the target, and writes CASH_TRANSFER + CASH_CREDIT rows', async () => {
    await expect(fundTransfer({ targetAccountNumber: john.accountNumber, amount: 1_000, pin: jane.pin })).resolves.toEqual({
      msg: 'Fund transferred successfully',
    });
    expect(await balance()).toBe(47_200);
    const [mine] = await getTransactions();
    expect(mine).toMatchObject({ transactionType: 'CASH_TRANSFER', amount: 1_000, targetAccountNumber: john.accountNumber });

    await signInAs(john);
    expect(await balance()).toBe(john.balance + 1_000);
    const [theirs] = await getTransactions();
    expect(theirs).toMatchObject({ transactionType: 'CASH_CREDIT', amount: 1_000, sourceAccountNumber: jane.accountNumber });
  });

  it('rejects transfers to self (400) and to unknown accounts (404)', async () => {
    expect(await err(fundTransfer({ targetAccountNumber: jane.accountNumber, amount: 100, pin: jane.pin }))).toMatchObject({
      status: 400,
      message: 'Source and target account cannot be the same',
    });
    expect(await err(fundTransfer({ targetAccountNumber: 'ZZZZZZ', amount: 100, pin: jane.pin }))).toMatchObject({
      status: 404,
      message: 'Account does not exist',
    });
  });
});

describe('otp + session', () => {
  it('accepts any 6-digit OTP and rejects others with 401 "Invalid OTP"', async () => {
    await generateOtp({ identifier: jane.email });
    await expect(verifyOtp({ identifier: jane.email, otp: '000000' })).resolves.toHaveProperty('token');
    expect(await err(verifyOtp({ identifier: jane.email, otp: '12345' }))).toMatchObject({ status: 401, message: 'Invalid OTP' });
  });

  it('logout revokes the token', async () => {
    await signInAs(jane);
    const token = tokenStore.get()!;
    await logout();
    expect(tokenStore.get()).toBeNull();
    expect(getMockState().revokedTokens.has(token)).toBe(true);
  });
});

describe('profile + otp limits', () => {
  it('rejects a profile update that reuses another user\'s phone number', async () => {
    await signInAs(jane);
    const { updateUser } = await import('@/api/endpoints');
    const body = { name: jane.name, email: jane.email, password: jane.password, countryCode: 'US', phoneNumber: john.phoneNumber, address: jane.address };
    expect(await err(updateUser(body))).toMatchObject({ status: 500 });
    await expect(updateUser({ ...body, phoneNumber: jane.phoneNumber })).resolves.toMatchObject({ phoneNumber: jane.phoneNumber });
  });

  it('locks OTP sends after 3 and unlocks 10 minutes after the first rejection', async () => {
    const { generateOtp: send } = await import('./db');
    const t0 = 1_000_000;
    for (let i = 0; i < 3; i++) send(jane.email, t0 + i);
    expect(() => send(jane.email, t0 + 5 * 60_000)).toThrow(/OTP generation limit exceeded/);
    expect(() => send(jane.email, t0 + 12 * 60_000)).toThrow(/OTP generation limit exceeded/);
    expect(() => send(jane.email, t0 + 16 * 60_000)).not.toThrow();
  });

  it('allows a new OTP once the last one is older than the 15-minute retry window', async () => {
    const { generateOtp: send } = await import('./db');
    const t0 = 1_000_000;
    for (let i = 0; i < 3; i++) send(john.email, t0 + i);
    expect(() => send(john.email, t0 + 16 * 60_000)).not.toThrow();
  });
});
