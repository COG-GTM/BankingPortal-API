import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { setUnauthorizedHandler } from '@/api/client';
import { logout as apiLogout } from '@/api/endpoints';
import { tokenStore } from '@/api/token';
import { Spinner } from '@/ui';
import { useToast } from './toast';

export interface AuthContextValue {
  token: string | null;
  isAuthenticated: boolean;
  /** Store a token from /users/login or /users/verify-otp. */
  signIn: (token: string) => void;
  /** Calls /users/logout, clears the token + query cache, and goes to /login. */
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => tokenStore.get());
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  useEffect(() => tokenStore.subscribe(setToken), []);

  useEffect(
    () =>
      setUnauthorizedHandler(() => {
        queryClient.clear();
        toast.error('Your session has expired. Please log in again.');
        navigate('/login', { replace: true });
      }),
    [navigate, queryClient, toast],
  );

  const signIn = useCallback((t: string) => tokenStore.set(t), []);

  const signOut = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      queryClient.clear();
      navigate('/login', { replace: true });
    }
  }, [navigate, queryClient]);

  const value = useMemo(
    () => ({ token, isAuthenticated: Boolean(token), signIn, signOut }),
    [token, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

/** Route guard: unauthenticated users go to /login with `state.from` set. */
export function RequireAuth({ children }: { children?: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location }} />;
  return children ? <>{children}</> : <Outlet />;
}

/** Inverse guard for /login etc.: signed-in users go to the dashboard. */
export function RedirectIfAuthenticated({ children }: { children?: ReactNode }) {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) return <Navigate to="/" replace />;
  return children ? <>{children}</> : <Outlet />;
}

export function PageLoader() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner />
    </div>
  );
}
