import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { server } from '@/mocks/node';
import { ApiError, extractErrorMessage, isApiError, parseBody, request, setUnauthorizedHandler } from './client';
import { logout, parsePinStatus } from './endpoints';
import { tokenStore } from './token';

describe('parseBody', () => {
  it('parses JSON bodies', () => {
    expect(parseBody('{"msg": "Cash deposited successfully"}')).toEqual({ msg: 'Cash deposited successfully' });
    expect(parseBody('{ "token": "abc" }')).toEqual({ token: 'abc' });
  });

  it('falls back to raw text for plain-text bodies', () => {
    expect(parseBody('Insufficient balance')).toBe('Insufficient balance');
    expect(parseBody('')).toBeNull();
  });
});

describe('extractErrorMessage', () => {
  it('uses plain text, then {message}, then {msg}, then a status fallback', () => {
    expect(extractErrorMessage('Invalid PIN', 401)).toBe('Invalid PIN');
    expect(extractErrorMessage({ message: 'Token is invalid' }, 401)).toBe('Token is invalid');
    expect(extractErrorMessage({ msg: 'x' }, 400)).toBe('x');
    expect(extractErrorMessage('', 503)).toMatch(/503/);
  });
});

describe('request', () => {
  it('attaches the bearer token and returns parsed JSON', async () => {
    let auth: string | null = null;
    server.use(
      http.get('/api/probe', ({ request: r }) => {
        auth = r.headers.get('Authorization');
        return new HttpResponse('{"ok": true}', { headers: { 'Content-Type': 'text/plain' } });
      }),
    );
    tokenStore.set('t0k3n');
    await expect(request('/probe')).resolves.toEqual({ ok: true });
    expect(auth).toBe('Bearer t0k3n');
  });

  it('throws ApiError {status, message} for plain-text errors', async () => {
    server.use(http.post('/api/probe', () => new HttpResponse('Amount must be in multiples of 100', { status: 400 })));
    const err = await request('/probe', { method: 'POST', body: {} }).catch((e: unknown) => e);
    expect(isApiError(err)).toBe(true);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 400, message: 'Amount must be in multiples of 100' });
  });

  it('clears the token and calls the 401 handler when the session is invalid', async () => {
    const onUnauthorized = vi.fn();
    const restore = setUnauthorizedHandler(onUnauthorized);
    server.use(http.get('/api/probe', () => HttpResponse.json({ status: 401, message: 'Token is invalid' }, { status: 401 })));
    tokenStore.set('expired');
    await expect(request('/probe')).rejects.toMatchObject({ status: 401, message: 'Token is invalid' });
    expect(tokenStore.get()).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledOnce();
    restore();
  });

  it('keeps the session for business 401s such as "Invalid PIN"', async () => {
    const onUnauthorized = vi.fn();
    const restore = setUnauthorizedHandler(onUnauthorized);
    server.use(http.post('/api/probe', () => new HttpResponse('Invalid PIN', { status: 401 })));
    tokenStore.set('good');
    await expect(request('/probe', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 401, message: 'Invalid PIN' });
    expect(tokenStore.get()).toBe('good');
    expect(onUnauthorized).not.toHaveBeenCalled();
    restore();
  });
});

describe('endpoint quirks', () => {
  it('maps the /pin/check sentence to a boolean', () => {
    expect(parsePinStatus('PIN has been created for this account')).toBe(true);
    expect(parsePinStatus('PIN has not been created for this account')).toBe(false);
  });

  it('logout clears the token even when the server errors', async () => {
    server.use(http.get('/api/users/logout', () => new HttpResponse('boom', { status: 500 })));
    tokenStore.set('abc');
    await logout().catch(() => {});
    expect(tokenStore.get()).toBeNull();
  });
});
