import { Link } from 'react-router-dom';
import { TodoStub } from '@/ui';

export function RegisterPage() {
  return (
    <div className="space-y-4">
      <TodoStub title="Open an account">
        <p>Registration form (name, email, password, country code, phone, address).</p>
        <p>Use <code>useRegister()</code>; show the new account number from the <code>UserResponse</code>.</p>
      </TodoStub>
      <Link className="text-sm font-medium text-brand-700 hover:underline" to="/login">
        Back to log in
      </Link>
    </div>
  );
}
