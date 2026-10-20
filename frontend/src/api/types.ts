/**
 * Mirrors of the Spring Boot DTOs in src/main/java/com/webapp/bankingportal/dto.
 * Responses are serialized by JsonUtil (Jackson, NON_NULL), so null fields are omitted
 * and java.util.Date is serialized as epoch milliseconds.
 */

/** entity/TransactionType.java */
export const TRANSACTION_TYPES = ['CASH_WITHDRAWAL', 'CASH_DEPOSIT', 'CASH_TRANSFER', 'CASH_CREDIT'] as const;
export type TransactionType = (typeof TRANSACTION_TYPES)[number];

/** dto/UserResponse.java */
export interface UserResponse {
  name: string;
  email: string;
  countryCode: string;
  phoneNumber: string;
  address: string;
  accountNumber: string;
  ifscCode: string;
  branch: string;
  accountType: string;
}

/** dto/AccountResponse.java */
export interface AccountResponse {
  accountNumber: string;
  balance: number;
  accountType: string;
  branch: string;
  ifscCode: string;
}

/** dto/TransactionDTO.java. `targetAccountNumber` is "N/A" for deposits and withdrawals. */
export interface TransactionDTO {
  id: number;
  amount: number;
  transactionType: TransactionType;
  /** Epoch milliseconds (Jackson default for java.util.Date). */
  transactionDate: number;
  sourceAccountNumber: string;
  targetAccountNumber: string;
}

/** dto/LoginRequest.java. `identifier` is an email or a 6-char account number. */
export interface LoginRequest {
  identifier: string;
  password: string;
}

/** dto/OtpRequest.java */
export interface OtpRequest {
  identifier: string;
}

/** dto/OtpVerificationRequest.java */
export interface OtpVerificationRequest {
  identifier: string;
  otp: string;
}

/** dto/ResetPasswordRequest.java */
export interface ResetPasswordRequest {
  identifier: string;
  resetToken: string;
  newPassword: string;
}

/** dto/PinRequest.java. `accountNumber` is ignored by the server (taken from the JWT). */
export interface PinRequest {
  accountNumber?: string;
  pin: string;
  password: string;
}

/** dto/PinUpdateRequest.java */
export interface PinUpdateRequest {
  accountNumber?: string;
  oldPin: string;
  newPin: string;
  password: string;
}

/** dto/AmountRequest.java (deposit / withdraw) */
export interface AmountRequest {
  accountNumber?: string;
  pin: string;
  amount: number;
}

/** dto/FundTransferRequest.java. The source account comes from the JWT. */
export interface FundTransferRequest {
  sourceAccountNumber?: string;
  targetAccountNumber: string;
  amount: number;
  pin: string;
}

/** entity/User.java fields accepted by POST /api/users/register. */
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  countryCode: string;
  phoneNumber: string;
  address: string;
}

/**
 * POST /api/users/update takes the same User fields. `password` must be the CURRENT password:
 * it is used to authenticate and is not changed.
 */
export type UpdateUserRequest = RegisterRequest;

// ---- Success bodies (ApiMessages.java) ----

/** TOKEN_ISSUED_SUCCESS: `{ "token": "%s" }` */
export interface TokenResponse {
  token: string;
}

/** OTP_SENT_SUCCESS, PASSWORD_RESET_SUCCESS, send-statement: `{"message": "..."}` */
export interface MessageResponse {
  message: string;
}

/** CASH_DEPOSIT_SUCCESS / CASH_WITHDRAWAL_SUCCESS / CASH_TRANSFER_SUCCESS: `{"msg": "..."}` */
export interface MsgResponse {
  msg: string;
}

/** PIN_CREATION_SUCCESS / PIN_UPDATE_SUCCESS: `{"hasPIN": bool, "msg": "..."}` */
export interface PinResponse {
  hasPIN: boolean;
  msg: string;
}

/** PASSWORD_RESET_TOKEN_ISSUED: `{"passwordResetToken": "%s"}` */
export interface PasswordResetTokenResponse {
  passwordResetToken: string;
}
