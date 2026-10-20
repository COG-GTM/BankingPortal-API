import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { server } from '@/mocks/node';
import { resetMockDb } from '@/mocks/db';
import { setUnauthorizedHandler } from '@/api/client';
import { tokenStore } from '@/api/token';

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
  setUnauthorizedHandler(() => {});
  vi.spyOn(console, 'info').mockImplementation(() => {});
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetMockDb();
  tokenStore.clear();
});

afterAll(() => server.close());
