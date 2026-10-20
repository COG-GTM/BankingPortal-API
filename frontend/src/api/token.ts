const STORAGE_KEY = 'onestopbank.token';

type Listener = (token: string | null) => void;

let memoryToken: string | null = readStorage();
const listeners = new Set<Listener>();

function readStorage(): string | null {
  try {
    return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStorage(token: string | null) {
  try {
    if (typeof sessionStorage === 'undefined') return;
    if (token) sessionStorage.setItem(STORAGE_KEY, token);
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // storage unavailable (private mode); memory copy still works
  }
}

/** JWT kept in memory and mirrored to sessionStorage so a reload keeps the session. */
export const tokenStore = {
  get(): string | null {
    return memoryToken;
  },
  set(token: string | null) {
    memoryToken = token;
    writeStorage(token);
    listeners.forEach((l) => l(token));
  },
  clear() {
    tokenStore.set(null);
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
