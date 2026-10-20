import type { ReactNode } from 'react';

/** Placeholder used by Wave 1 stub pages; Wave 2 replaces it. */
export function TodoStub({ title, children, level = 1 }: { title: string; children?: ReactNode; level?: 1 | 2 }) {
  const Heading = level === 1 ? 'h1' : 'h2';
  return (
    <div className="space-y-4">
      <Heading className={level === 1 ? 'text-2xl font-bold text-navy-900' : 'text-lg font-semibold text-navy-900'}>{title}</Heading>
      <div className="rounded-xl border border-dashed border-navy-300 bg-navy-50 p-5 text-sm text-navy-800">
        <p className="font-semibold">TODO (Wave 2)</p>
        {children && <div className="mt-2 space-y-1">{children}</div>}
      </div>
    </div>
  );
}
