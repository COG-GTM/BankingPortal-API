import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { ApiError, extractErrorMessage, parseBody, rawRequest, request } from './client';
import { tokenStore } from './token';
import type {
  AccountResponse,
  AmountRequest,
  FundTransferRequest,
  LoginRequest,
  MessageResponse,
  MsgResponse,
  OtpRequest,
  OtpVerificationRequest,
  PasswordResetTokenResponse,
  PinRequest,
  PinResponse,
  PinUpdateRequest,
  RegisterRequest,
  ResetPasswordRequest,
  TokenResponse,
  TransactionDTO,
  UpdateUserRequest,
  UserResponse,
} from './types';

// ---------------------------------------------------------------------------
// Users (controller/UserController.java)
// ---------------------------------------------------------------------------

/** POST /api/users/register (public) → UserResponse incl. the new accountNumber. */
export const registerUser = (body: RegisterRequest) =>
  request<UserResponse>('/users/register', { method: 'POST', body, auth: false });

/** POST /api/users/login (public) → `{ "token" }`. */
export const login = (body: LoginRequest) =>
  request<TokenResponse>('/users/login', { method: 'POST', body, auth: false });

/** POST /api/users/generate-otp (public) → `{"message": "OTP sent successfully to: <email>"}`. */
export const generateOtp = (body: OtpRequest) =>
  request<MessageResponse>('/users/generate-otp', { method: 'POST', body, auth: false });

/** POST /api/users/verify-otp (public) → `{ "token" }`. */
export const verifyOtp = (body: OtpVerificationRequest) =>
  request<TokenResponse>('/users/verify-otp', { method: 'POST', body, auth: false });

/** POST /api/users/update (JWT). `password` is the current password (auth only). */
export const updateUser = (body: UpdateUserRequest) =>
  request<UserResponse>('/users/update', { method: 'POST', body });

/**
 * GET /api/users/logout (JWT). The backend returns a `redirect:/logout` ModelAndView, so any
 * 2xx/3xx (or an opaque redirect) counts as success. The local token is always cleared.
 */
export async function logout(): Promise<void> {
  try {
    const res = await rawRequest('/users/logout', { redirect: 'manual' });
    const ok = res.type === 'opaqueredirect' || (res.status >= 200 && res.status < 400);
    if (!ok) {
      const body = parseBody(await res.text());
      throw new ApiError(res.status, extractErrorMessage(body, res.status), body);
    }
  } finally {
    tokenStore.clear();
  }
}

// ---------------------------------------------------------------------------
// Password reset (controller/AuthController.java)
// ---------------------------------------------------------------------------

/** POST /api/auth/password-reset/send-otp (public). */
export const sendPasswordResetOtp = (body: OtpRequest) =>
  request<MessageResponse>('/auth/password-reset/send-otp', { method: 'POST', body, auth: false });

/** POST /api/auth/password-reset/verify-otp (public) → `{"passwordResetToken"}`. */
export const verifyPasswordResetOtp = (body: OtpVerificationRequest) =>
  request<PasswordResetTokenResponse>('/auth/password-reset/verify-otp', {
    method: 'POST',
    body,
    auth: false,
  });

/** POST /api/auth/password-reset (public). The reset token is single-use. */
export const resetPassword = (body: ResetPasswordRequest) =>
  request<MessageResponse>('/auth/password-reset', { method: 'POST', body, auth: false });

// ---------------------------------------------------------------------------
// Dashboard (controller/DashboardController.java)
// ---------------------------------------------------------------------------

/** GET /api/dashboard/user (JWT). */
export const getUserDetails = (signal?: AbortSignal) =>
  request<UserResponse>('/dashboard/user', { signal });

/** GET /api/dashboard/account (JWT). */
export const getAccountDetails = (signal?: AbortSignal) =>
  request<AccountResponse>('/dashboard/account', { signal });

// ---------------------------------------------------------------------------
// Account (controller/AccountController.java)
// ---------------------------------------------------------------------------

export const PIN_CREATED_MARKER = 'PIN has been created';

/** Maps the /pin/check sentence to a boolean. "PIN has not been created…" → false. */
export function parsePinStatus(body: unknown): boolean {
  return typeof body === 'string' && body.includes(PIN_CREATED_MARKER);
}

/** GET /api/account/pin/check (JWT) → true when a PIN exists. */
export const checkPin = async (signal?: AbortSignal) =>
  parsePinStatus(await request<unknown>('/account/pin/check', { signal }));

/** POST /api/account/pin/create (JWT, idempotent) → `{"hasPIN": true, "msg"}`. */
export const createPin = (body: PinRequest) =>
  request<PinResponse>('/account/pin/create', { method: 'POST', body });

/** POST /api/account/pin/update (JWT, idempotent) → `{"hasPIN": false, "msg"}` (sic). */
export const updatePin = (body: PinUpdateRequest) =>
  request<PinResponse>('/account/pin/update', { method: 'POST', body });

/** POST /api/account/deposit (JWT, idempotent) → `{"msg": "Cash deposited successfully"}`. */
export const deposit = (body: AmountRequest) =>
  request<MsgResponse>('/account/deposit', { method: 'POST', body });

/** POST /api/account/withdraw (JWT, idempotent) → `{"msg": "Cash withdrawn successfully"}`. */
export const withdraw = (body: AmountRequest) =>
  request<MsgResponse>('/account/withdraw', { method: 'POST', body });

/** POST /api/account/fund-transfer (JWT, idempotent) → `{"msg": "Fund transferred successfully"}`. */
export const fundTransfer = (body: FundTransferRequest) =>
  request<MsgResponse>('/account/fund-transfer', { method: 'POST', body });

/** GET /api/account/transactions (JWT) → TransactionDTO[] sorted newest first. */
export const getTransactions = (signal?: AbortSignal) =>
  request<TransactionDTO[]>('/account/transactions', { signal });

/** GET /api/account/send-statement (JWT) → `{"message": "Bank statement sent to your email."}`. */
export const sendStatement = () => request<MessageResponse>('/account/send-statement');

// ---------------------------------------------------------------------------
// TanStack Query keys + hooks
// ---------------------------------------------------------------------------

export const queryKeys = {
  all: ['bank'] as const,
  user: () => [...queryKeys.all, 'dashboard', 'user'] as const,
  account: () => [...queryKeys.all, 'dashboard', 'account'] as const,
  pinStatus: () => [...queryKeys.all, 'account', 'pin'] as const,
  transactions: () => [...queryKeys.all, 'account', 'transactions'] as const,
};

export function useUserDetails(options: { enabled?: boolean } = {}) {
  return useQuery({ queryKey: queryKeys.user(), queryFn: ({ signal }) => getUserDetails(signal), ...options });
}

export function useAccountDetails(options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.account(),
    queryFn: ({ signal }) => getAccountDetails(signal),
    ...options,
  });
}

export function usePinStatus(options: { enabled?: boolean } = {}) {
  return useQuery({ queryKey: queryKeys.pinStatus(), queryFn: ({ signal }) => checkPin(signal), ...options });
}

export function useTransactions(options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.transactions(),
    queryFn: ({ signal }) => getTransactions(signal),
    ...options,
  });
}

/** Refetch balance + history. Call after every money movement (idempotent replays included). */
export function invalidateAccountData(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.account() }),
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions() }),
  ]);
}

// Mutation hooks: thin wrappers so feature folders share cache invalidation rules.

export const useLogin = () => useMutation({ mutationFn: login });
export const useGenerateOtp = () => useMutation({ mutationFn: generateOtp });
export const useVerifyOtp = () => useMutation({ mutationFn: verifyOtp });
export const useRegister = () => useMutation({ mutationFn: registerUser });
export const useSendPasswordResetOtp = () => useMutation({ mutationFn: sendPasswordResetOtp });
export const useVerifyPasswordResetOtp = () => useMutation({ mutationFn: verifyPasswordResetOtp });
export const useResetPassword = () => useMutation({ mutationFn: resetPassword });
export const useSendStatement = () => useMutation({ mutationFn: sendStatement });

export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: updateUser,
    onSuccess: (user) => qc.setQueryData(queryKeys.user(), user),
  });
}

function useMoneyMutation<TBody>(fn: (body: TBody) => Promise<MsgResponse>) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: fn, onSettled: () => invalidateAccountData(qc) });
}

export const useDeposit = () => useMoneyMutation(deposit);
export const useWithdraw = () => useMoneyMutation(withdraw);
export const useFundTransfer = () => useMoneyMutation(fundTransfer);

function usePinMutation<TBody>(fn: (body: TBody) => Promise<PinResponse>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSettled: () => qc.invalidateQueries({ queryKey: queryKeys.pinStatus() }),
  });
}

export const useCreatePin = () => usePinMutation(createPin);
export const useUpdatePin = () => usePinMutation(updatePin);
