const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const dateFmt = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
const dateTimeFmt = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
});

export function formatMoney(amount: number): string {
  return currency.format(amount);
}

export function formatDate(value: number | string | Date): string {
  return dateFmt.format(new Date(value));
}

export function formatDateTime(value: number | string | Date): string {
  return dateTimeFmt.format(new Date(value));
}

/** ISO yyyy-mm-dd in local time, e.g. for CSV export. */
export function formatIsoDate(value: number | string | Date): string {
  const d = new Date(value);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}
