import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { resetMockDb } from './db';

export const worker = setupWorker(...handlers);

export async function startMockWorker() {
  await worker.start({ onUnhandledRequest: 'bypass', quiet: import.meta.env.PROD });
  // Handy in the console and for Playwright: window.__resetMockDb()
  (window as unknown as { __resetMockDb: typeof resetMockDb }).__resetMockDb = resetMockDb;
}
