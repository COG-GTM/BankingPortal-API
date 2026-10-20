import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ invalid, className, ...rest }, ref) {
  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        'block h-10 w-full rounded-lg border bg-white px-3 text-sm text-navy-950 placeholder:text-slate-400',
        'focus:outline-none focus:ring-2 disabled:bg-slate-50 disabled:text-slate-500',
        invalid ? 'border-red-500 focus:ring-red-200' : 'border-slate-300 focus:border-brand-600 focus:ring-brand-100',
        className,
      )}
      {...rest}
    />
  );
});
