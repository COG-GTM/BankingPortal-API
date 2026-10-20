import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface CardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  title?: ReactNode;
  actions?: ReactNode;
  /** `brand` = navy panel with white text (e.g. the balance card). */
  tone?: 'default' | 'brand';
}

export function Card({ title, actions, tone = 'default', className, children, ...rest }: CardProps) {
  return (
    <section
      className={cn(
        'rounded-xl border p-5 shadow-sm',
        tone === 'brand' ? 'border-navy-800 bg-navy-900 text-white' : 'border-slate-200 bg-white',
        className,
      )}
      {...rest}
    >
      {(title || actions) && (
        <header className="mb-4 flex items-center justify-between gap-4">
          {title && <h2 className={cn('text-base font-semibold', tone === 'brand' ? 'text-white' : 'text-navy-900')}>{title}</h2>}
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}
