import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/cn';
import { formatMoney } from '@/lib/format';

export interface MoneyTextProps extends HTMLAttributes<HTMLSpanElement> {
  amount: number;
  /** `credit` prefixes "+" in green, `debit` prefixes "−" in red. */
  sign?: 'credit' | 'debit';
}

export function MoneyText({ amount, sign, className, ...rest }: MoneyTextProps) {
  const prefix = sign === 'credit' ? '+' : sign === 'debit' ? '−' : '';
  return (
    <span
      className={cn(
        'tabular-nums',
        sign === 'credit' && 'text-emerald-700',
        sign === 'debit' && 'text-red-600',
        className,
      )}
      {...rest}
    >
      {prefix}
      {formatMoney(Math.abs(amount))}
    </span>
  );
}
