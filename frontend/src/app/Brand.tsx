import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';

export const BRAND_NAME = 'OneStopBank';

export function Brand({ inverted = false, className }: { inverted?: boolean; className?: string }) {
  return (
    <Link to="/" className={cn('inline-flex items-center gap-2 text-lg font-bold tracking-tight', className)}>
      <span
        aria-hidden
        className={cn(
          'flex h-8 w-8 items-center justify-center rounded-lg text-sm font-black',
          inverted ? 'bg-white text-navy-900' : 'bg-navy-900 text-white',
        )}
      >
        O
      </span>
      <span className={inverted ? 'text-white' : 'text-navy-900'}>{BRAND_NAME}</span>
    </Link>
  );
}
