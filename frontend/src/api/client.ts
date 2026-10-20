import { tokenStore } from './token';

export const API_BASE = '/api';

export class ApiError extends Error {
  readonly status: number;
  /** Parsed body when the error body was JSON, otherwise the raw text. */
  readonly body: unknown;

  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/**
 * Every backend response is a String. Successes are usually JSON text, errors are often plain
 * text ("Insufficient balance"). Try JSON first and fall back to the raw text.
 */
export function parseBody(text: string): unknown {
  if (text === '') return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** Pulls a human-readable message out of a plain-text or JSON error body. */
export function extractErrorMessage(body: unknown, status: number): string {
  if (typeof body === 'string' && body.trim()) return body.trim();
  if (body && typeof body === 'object') {
    const rec = body as Record<string, unknown>;
    for (const key of ['message', 'msg', 'error']) {
      const v = rec[key];
      if (typeof v === 'string' && v.trim()) return v;
    }
  }
  return `Request failed with status ${status}`;
}

/**
 * 401s that are business errors, not an expired session. The backend uses
 * UnauthorizedException (401) for wrong PIN / password, so these must NOT log the user out.
 */
const BUSINESS_401_MESSAGES = [
  'Invalid PIN',
  'PIN cannot be empty',
  'PIN has not been created for this account',
  'PIN already created',
  'Invalid password',
  'Password cannot be empty',
  'Bad credentials',
  'Invalid OTP',
];

export function isSessionExpired(status: number, message: string): boolean {
  return status === 401 && !BUSINESS_401_MESSAGES.includes(message);
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler = () => {
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
};

/** AuthProvider registers a router-aware handler; the default does a hard redirect. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  const previous = unauthorizedHandler;
  unauthorizedHandler = handler;
  return () => {
    unauthorizedHandler = previous;
  };
}

export interface RequestOptions {
  method?: 'GET' | 'POST';
  body?: unknown;
  /** Attach the bearer token (default true). Public endpoints pass false. */
  auth?: boolean;
  signal?: AbortSignal;
  redirect?: RequestRedirect;
}

export function apiUrl(path: string): string {
  const relative = `${API_BASE}${path}`;
  if (typeof window !== 'undefined' && window.location?.origin) {
    return new URL(relative, window.location.origin).toString();
  }
  return relative;
}

export async function rawRequest(path: string, options: RequestOptions = {}): Promise<Response> {
  const { method = 'GET', body, auth = true, signal, redirect } = options;
  const headers: Record<string, string> = { Accept: 'application/json, text/plain, */*' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = tokenStore.get();
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  return fetch(apiUrl(path), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
    redirect,
  });
}

/** Typed request: resolves with the parsed body or throws ApiError {status, message}. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const auth = options.auth ?? true;
  const res = await rawRequest(path, options);
  const parsed = parseBody(await res.text());

  if (!res.ok) {
    const message = extractErrorMessage(parsed, res.status);
    if (auth && isSessionExpired(res.status, message)) {
      tokenStore.clear();
      unauthorizedHandler();
    }
    throw new ApiError(res.status, message, parsed);
  }

  return parsed as T;
}

/** Message for UI display from any thrown value. */
export function getErrorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Something went wrong';
}
