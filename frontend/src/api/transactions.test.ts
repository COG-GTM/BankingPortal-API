import { describe, expect, it } from 'vitest';
import { directionFor } from './transactions';

describe('directionFor', () => {
  const transfer = { transactionType: 'CASH_TRANSFER' as const, sourceAccountNumber: '7F3K9Q', targetAccountNumber: '2M8PLX' };

  it('treats a shared transfer row as a debit for the sender and a credit for the recipient', () => {
    expect(directionFor(transfer, '7F3K9Q')).toBe('debit');
    expect(directionFor(transfer, '2M8PLX')).toBe('credit');
  });

  it('maps deposits to credits and withdrawals to debits', () => {
    expect(directionFor({ transactionType: 'CASH_DEPOSIT', sourceAccountNumber: '7F3K9Q', targetAccountNumber: 'N/A' }, '7F3K9Q')).toBe('credit');
    expect(directionFor({ transactionType: 'CASH_WITHDRAWAL', sourceAccountNumber: '7F3K9Q', targetAccountNumber: 'N/A' }, '7F3K9Q')).toBe('debit');
  });
});
