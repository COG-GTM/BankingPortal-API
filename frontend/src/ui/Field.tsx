import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface FieldProps {
  label: ReactNode;
  /** A single form control; it receives `id`, `aria-describedby` and `invalid`. */
  children: ReactElement;
  error?: string;
  hint?: ReactNode;
  className?: string;
}

/** Label + control + hint/error, wired for accessibility. Pass `errors.x?.message` from react-hook-form. */
export function Field({ label, children, error, hint, className }: FieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;
  const control = isValidElement<Record<string, unknown>>(children)
    ? cloneElement(children, { id, 'aria-describedby': describedBy, invalid: Boolean(error) })
    : children;

  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={id} className="block text-sm font-medium text-navy-900">
        {label}
      </label>
      {control}
      {hint && !error && (
        <p id={hintId} className="text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
