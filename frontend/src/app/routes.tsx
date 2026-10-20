import { Navigate, Outlet, type RouteObject } from 'react-router-dom';
import { ForgotPasswordPage, LoginPage, RegisterPage } from '@/features/auth';
import { DashboardPage } from '@/features/dashboard';
import { MoveMoneyPage } from '@/features/payments';
import { ProfilePage } from '@/features/profile';
import { SecurityPage } from '@/features/security';
import { TransactionsPage } from '@/features/transactions';
import { AuthProvider, RedirectIfAuthenticated, RequireAuth } from './auth';
import { AppLayout, PublicLayout } from './layouts';

/** Every route path in the app. Wave 2 links to these; only Wave 1 edits this file. */
export const paths = {
  login: '/login',
  register: '/register',
  forgotPassword: '/forgot-password',
  dashboard: '/',
  transactions: '/transactions',
  moveMoney: '/move-money',
  deposit: '/move-money/deposit',
  withdraw: '/move-money/withdraw',
  transfer: '/move-money/transfer',
  security: '/security',
  profile: '/profile',
} as const;

function Root() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  );
}

export const routes: RouteObject[] = [
  {
    element: <Root />,
    children: [
      {
        element: (
          <RedirectIfAuthenticated>
            <PublicLayout />
          </RedirectIfAuthenticated>
        ),
        children: [
          { path: paths.login, element: <LoginPage /> },
          { path: paths.register, element: <RegisterPage /> },
          { path: paths.forgotPassword, element: <ForgotPasswordPage /> },
        ],
      },
      {
        element: (
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        ),
        children: [
          { index: true, element: <DashboardPage /> },
          { path: paths.transactions, element: <TransactionsPage /> },
          { path: `${paths.moveMoney}/:tab?`, element: <MoveMoneyPage /> },
          { path: paths.security, element: <SecurityPage /> },
          { path: paths.profile, element: <ProfilePage /> },
        ],
      },
      { path: '*', element: <Navigate to={paths.dashboard} replace /> },
    ],
  },
];
