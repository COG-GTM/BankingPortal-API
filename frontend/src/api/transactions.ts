import type { TransactionDTO } from './types';

export type TransactionDirection = 'credit' | 'debit';

/**
 * Money in or out for `myAccountNumber`. The backend returns ONE shared CASH_TRANSFER row to both
 * parties, so a transfer is a credit for its target and a debit for its source.
 */
export function directionFor(
  tx: Pick<TransactionDTO, 'transactionType' | 'sourceAccountNumber' | 'targetAccountNumber'>,
  myAccountNumber: string,
): TransactionDirection {
  switch (tx.transactionType) {
    case 'CASH_DEPOSIT':
    case 'CASH_CREDIT':
      return 'credit';
    case 'CASH_WITHDRAWAL':
      return 'debit';
    case 'CASH_TRANSFER':
      return tx.targetAccountNumber === myAccountNumber && tx.sourceAccountNumber !== myAccountNumber ? 'credit' : 'debit';
  }
}
