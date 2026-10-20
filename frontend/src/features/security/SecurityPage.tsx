import { TodoStub } from '@/ui';

export function SecurityPage() {
  return (
    <TodoStub title="Security">
      <p>
        PIN status from <code>usePinStatus()</code>; create with <code>useCreatePin()</code> or change with{' '}
        <code>useUpdatePin()</code>.
      </p>
    </TodoStub>
  );
}
