import { NavLink, Navigate, useParams } from 'react-router-dom';
import { cn } from '@/lib/cn';
import { TodoStub } from '@/ui';

export const MOVE_MONEY_TABS = [
  { id: 'deposit', label: 'Deposit', hook: 'useDeposit' },
  { id: 'withdraw', label: 'Withdraw', hook: 'useWithdraw' },
  { id: 'transfer', label: 'Transfer', hook: 'useFundTransfer' },
] as const;
export type MoveMoneyTab = (typeof MOVE_MONEY_TABS)[number]['id'];

export function MoveMoneyPage() {
  const { tab } = useParams<{ tab?: string }>();
  const active = MOVE_MONEY_TABS.find((t) => t.id === tab);
  if (!active) return <Navigate to="/move-money/deposit" replace />;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-navy-900">Move money</h1>
      <div role="tablist" aria-label="Move money" className="inline-flex rounded-lg border border-slate-200 bg-white p-1">
        {MOVE_MONEY_TABS.map((t) => (
          <NavLink
            key={t.id}
            to={`/move-money/${t.id}`}
            role="tab"
            aria-selected={t.id === active.id}
            className={cn(
              'rounded-md px-4 py-1.5 text-sm font-medium',
              t.id === active.id ? 'bg-navy-900 text-white' : 'text-navy-800 hover:bg-navy-50',
            )}
          >
            {t.label}
          </NavLink>
        ))}
      </div>
      <TodoStub title={active.label} level={2}>
        <p>
          Form with amount + PIN, submitted via <code>{active.hook}()</code>. Amount rules: &gt; 0, multiple of 100, ≤ 100,000.
        </p>
      </TodoStub>
    </div>
  );
}
