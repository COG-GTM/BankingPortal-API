
export const MOCK_OTP = '123456';

export interface SeedUser {
  name: string;
  email: string;
  password: string;
  countryCode: string;
  phoneNumber: string;
  address: string;
  accountNumber: string;
  balance: number;
  pin: string | null;
}

/** Jane has a PIN and ~40 transactions; John has no PIN (exercises the "Create your PIN" path). */
export const SEED_USERS = {
  jane: {
    name: 'Jane Doe',
    email: 'jane@demo.bank',
    password: 'Demo@1234',
    countryCode: 'US',
    phoneNumber: '2025550143',
    address: '742 Evergreen Terrace, Springfield',
    accountNumber: '7F3K9Q',
    balance: 48_200,
    pin: '1234',
  },
  john: {
    name: 'John Smith',
    email: 'john@demo.bank',
    password: 'Demo@1234',
    countryCode: 'US',
    phoneNumber: '2025550178',
    address: '1600 Pennsylvania Ave NW, Washington',
    accountNumber: '2M8PLX',
    balance: 6_400,
    pin: null,
  },
} satisfies Record<string, SeedUser>;

/** A closed external account that only appears as the sender of historical credits. */
export const EXTERNAL_ACCOUNT = '9QW2RT';

/** `TRANSFER_IN` is a CASH_TRANSFER from `counterparty` into Jane's account (the backend has no separate credit row). */
export type SeedKind = 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL' | 'CASH_TRANSFER' | 'TRANSFER_IN';

export interface SeedTxn {
  daysAgo: number;
  hour: number;
  type: SeedKind;
  amount: number;
  /** Other account for transfers (target for CASH_TRANSFER, source for TRANSFER_IN). */
  counterparty?: string;
}

function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * 40 deterministic transactions for Jane over the last 90 days. The four most recent match the
 * scope wireframe (deposit 5,000 / transfer 1,200 to 2M8PLX / incoming transfer 800 from 9QW2RT / withdrawal 300).
 */
export function janeHistory(): SeedTxn[] {
  const rand = mulberry32(20261020);
  const pick = (min: number, max: number) => Math.round((min + rand() * (max - min)) / 100) * 100;
  const txns: SeedTxn[] = [
    { daysAgo: 2, hour: 10, type: 'CASH_DEPOSIT', amount: 5_000 },
    { daysAgo: 4, hour: 15, type: 'CASH_TRANSFER', amount: 1_200, counterparty: SEED_USERS.john.accountNumber },
    { daysAgo: 6, hour: 9, type: 'TRANSFER_IN', amount: 800, counterparty: EXTERNAL_ACCOUNT },
    { daysAgo: 8, hour: 18, type: 'CASH_WITHDRAWAL', amount: 300 },
  ];
  const types: SeedKind[] = [
    'CASH_DEPOSIT',
    'CASH_WITHDRAWAL',
    'CASH_WITHDRAWAL',
    'CASH_TRANSFER',
    'TRANSFER_IN',
    'CASH_WITHDRAWAL',
    'CASH_DEPOSIT',
    'CASH_TRANSFER',
    'TRANSFER_IN',
  ];
  for (let i = 0; i < 36; i++) {
    const daysAgo = 9 + Math.floor((i * 81) / 36 + rand() * 2);
    const type = types[i % types.length] as SeedKind;
    const hour = 8 + Math.floor(rand() * 11);
    switch (type) {
      case 'CASH_DEPOSIT':
        txns.push({ daysAgo, hour, type, amount: pick(1_000, 6_000) });
        break;
      case 'CASH_WITHDRAWAL':
        txns.push({ daysAgo, hour, type, amount: pick(100, 1_500) });
        break;
      case 'CASH_TRANSFER':
        txns.push({ daysAgo, hour, type, amount: pick(200, 2_500), counterparty: SEED_USERS.john.accountNumber });
        break;
      case 'TRANSFER_IN':
        txns.push({ daysAgo, hour, type, amount: pick(300, 3_000), counterparty: EXTERNAL_ACCOUNT });
        break;
    }
  }
  return txns;
}
