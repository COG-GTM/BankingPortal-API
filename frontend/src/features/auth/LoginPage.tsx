import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate, type Location } from 'react-router-dom';
import { z } from 'zod';
import { getErrorMessage } from '@/api/client';
import { useLogin } from '@/api/endpoints';
import { useAuth } from '@/app/auth';
import { Button, Field, Input } from '@/ui';

const schema = z.object({
  identifier: z.string().trim().min(1, 'Enter your email or account number'),
  password: z.string().min(1, 'Enter your password'),
});
type FormValues = z.infer<typeof schema>;

/** Wave 1: minimal password login. TODO (Wave 2 auth): OTP login tab, remember-me, polish. */
export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const login = useLogin();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    const { token } = await login.mutateAsync(values);
    signIn(token);
    const from = (location.state as { from?: Location } | null)?.from?.pathname ?? '/';
    navigate(from, { replace: true });
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Log in</h1>
        <p className="mt-1 text-sm text-slate-500">Welcome back to OneStopBank.</p>
      </div>
      <form className="space-y-4" onSubmit={(e) => void onSubmit(e).catch(() => {})} noValidate>
        <Field label="Email or account number" error={errors.identifier?.message}>
          <Input autoComplete="username" placeholder="jane@demo.bank" {...register('identifier')} />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <Input type="password" autoComplete="current-password" {...register('password')} />
        </Field>
        {login.isError && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {getErrorMessage(login.error)}
          </p>
        )}
        <Button type="submit" fullWidth loading={login.isPending}>
          Log in
        </Button>
      </form>
      <div className="flex justify-between text-sm">
        <Link className="font-medium text-brand-700 hover:underline" to="/forgot-password">
          Forgot password?
        </Link>
        <Link className="font-medium text-brand-700 hover:underline" to="/register">
          Open an account
        </Link>
      </div>
    </div>
  );
}
