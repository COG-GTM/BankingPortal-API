/** Client-side mirror of AccountServiceImpl#validateAmount. Returns the ApiMessages string or null. */
export const AMOUNT_RULES = { min: 0, multiple: 100, max: 100_000 } as const;

export function validateAmount(amount: number): string | null {
  if (!Number.isFinite(amount)) return 'Invalid amount';
  if (amount <= 0) return 'Amount must be greater than 0';
  if (amount % 100 !== 0) return 'Amount must be in multiples of 100';
  if (amount > 100_000) return 'Amount cannot be greater than 100,000';
  return null;
}

export const PIN_PATTERN = /^[0-9]{4}$/;
