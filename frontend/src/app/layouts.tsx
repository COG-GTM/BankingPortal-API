import { NavLink, Outlet } from 'react-router-dom';
import { useUserDetails } from '@/api/endpoints';
import { cn } from '@/lib/cn';
import { Button } from '@/ui';
import { useAuth } from './auth';
import { Brand } from './Brand';

export const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/move-money', label: 'Move money', end: false },
  { to: '/transactions', label: 'Transactions', end: false },
  { to: '/security', label: 'Security', end: false },
  { to: '/profile', label: 'Profile', end: false },
] as const;

export function AppLayout() {
  const { signOut } = useAuth();
  const user = useUserDetails();

  return (
    <div className="flex min-h-full flex-col">
      <header className="flex h-16 items-center justify-between bg-navy-900 px-6 text-white">
        <Brand inverted />
        <div className="flex items-center gap-4">
          <span className="text-sm text-navy-100" data-testid="topbar-user">
            {user.data ? (
              <>
                <span className="font-semibold text-white">{user.data.name}</span>
                <span className="ml-2 text-navy-200">· {user.data.accountNumber}</span>
              </>
            ) : (
              '…'
            )}
          </span>
          <Button variant="secondary" size="sm" onClick={() => void signOut()}>
            Log out
          </Button>
        </div>
      </header>
      <div className="flex flex-1">
        <nav aria-label="Main" className="w-56 shrink-0 border-r border-slate-200 bg-white p-4">
          <ul className="space-y-1">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    cn(
                      'block rounded-lg px-3 py-2 text-sm font-medium',
                      isActive ? 'bg-navy-900 text-white' : 'text-navy-800 hover:bg-navy-50',
                    )
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <main className="flex-1 p-8">
          <div className="mx-auto max-w-5xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}

export function PublicLayout() {
  return (
    <div className="flex min-h-full flex-col bg-gradient-to-br from-navy-950 via-navy-900 to-navy-700">
      <header className="px-8 py-6">
        <Brand inverted />
      </header>
      <main className="flex flex-1 items-start justify-center px-4 pb-16 pt-6">
        <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
