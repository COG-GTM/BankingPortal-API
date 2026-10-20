import { getErrorMessage } from '@/api/client';
import { useAccountDetails } from '@/api/endpoints';
import { Card, MoneyText, Spinner, TodoStub } from '@/ui';

/** Wave 1: live balance from /dashboard/account. TODO (Wave 2 dashboard): charts, recent activity, quick actions. */
export function DashboardPage() {
  const account = useAccountDetails();

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-navy-900">Dashboard</h1>
      <Card tone="brand" aria-label="Account balance">
        <p className="text-sm text-navy-200">Available balance</p>
        {account.isPending ? (
          <div className="mt-2">
            <Spinner />
          </div>
        ) : account.isError ? (
          <p role="alert" className="mt-2 text-red-200">
            {getErrorMessage(account.error)}
          </p>
        ) : (
          <>
            <MoneyText amount={account.data.balance} className="mt-1 block text-4xl font-bold" data-testid="balance" />
            <p className="mt-2 text-sm text-navy-200">
              {account.data.accountType} · {account.data.accountNumber} · {account.data.branch} ({account.data.ifscCode})
            </p>
          </>
        )}
      </Card>
      <TodoStub title="Dashboard widgets" level={2}>
        <p>Balance trend chart (Recharts), recent transactions, quick actions.</p>
      </TodoStub>
    </div>
  );
}
