import { Link } from 'react-router-dom';
import { TodoStub } from '@/ui';

export function ForgotPasswordPage() {
  return (
    <div className="space-y-4">
      <TodoStub title="Reset password">
        <p>3 steps: <code>useSendPasswordResetOtp</code> → <code>useVerifyPasswordResetOtp</code> → <code>useResetPassword</code>.</p>
      </TodoStub>
      <Link className="text-sm font-medium text-brand-700 hover:underline" to="/login">
        Back to log in
      </Link>
    </div>
  );
}
