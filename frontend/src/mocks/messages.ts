/** Verbatim strings from src/main/java/com/webapp/bankingportal/util/ApiMessages.java. */
export const ApiMessages = {
  ACCOUNT_NOT_FOUND: 'Account does not exist',
  AMOUNT_EXCEED_100_000_ERROR: 'Amount cannot be greater than 100,000',
  AMOUNT_INVALID_ERROR: 'Invalid amount',
  AMOUNT_NEGATIVE_ERROR: 'Amount must be greater than 0',
  AMOUNT_NOT_MULTIPLE_OF_100_ERROR: 'Amount must be in multiples of 100',
  BALANCE_INSUFFICIENT_ERROR: 'Insufficient balance',
  CASH_DEPOSIT_SUCCESS: '{"msg": "Cash deposited successfully"}',
  CASH_TRANSFER_SAME_ACCOUNT_ERROR: 'Source and target account cannot be the same',
  CASH_TRANSFER_SUCCESS: '{"msg": "Fund transferred successfully"}',
  CASH_WITHDRAWAL_SUCCESS: '{"msg": "Cash withdrawn successfully"}',
  IDENTIFIER_MISSING_ERROR: 'Missing identifier',
  OTP_GENERATION_LIMIT_EXCEEDED: 'OTP generation limit exceeded. Please try again after %d minutes',
  OTP_INVALID_ERROR: 'Invalid OTP',
  OTP_MISSING_ERROR: 'Missing OTP',
  OTP_SENT_SUCCESS: '{"message": "OTP sent successfully to: %s"}',
  PASSWORD_CONTAINS_WHITESPACE_ERROR: 'Password cannot contain any whitespace characters',
  PASSWORD_EMPTY_ERROR: 'Password cannot be empty',
  PASSWORD_INVALID_ERROR: 'Invalid password',
  PASSWORD_RESET_SUCCESS: '{"message": "Password reset successfully"}',
  PASSWORD_RESET_TOKEN_ISSUED: '{"passwordResetToken": "%s"}',
  PASSWORD_TOO_LONG_ERROR: 'Password must be less than 128 characters long',
  PASSWORD_TOO_SHORT_ERROR: 'Password must be at least 8 characters long',
  PIN_ALREADY_EXISTS: 'PIN already created',
  PIN_CREATED: 'PIN has been created for this account',
  PIN_CREATION_SUCCESS: '{"hasPIN": true, "msg": "PIN created successfully"}',
  PIN_EMPTY_ERROR: 'PIN cannot be empty',
  PIN_FORMAT_INVALID_ERROR: 'PIN must be 4 digits',
  PIN_INVALID_ERROR: 'Invalid PIN',
  PIN_NOT_CREATED: 'PIN has not been created for this account',
  PIN_UPDATE_SUCCESS: '{"hasPIN": false, "msg": "PIN updated successfully"}',
  TOKEN_INVALID_ERROR: 'Token is invalid',
  TOKEN_ISSUED_SUCCESS: '{ "token": "%s" }',
  TOKEN_NOT_FOUND_ERROR: 'Token not found',
  USER_ADDRESS_EMPTY_ERROR: 'Address cannot be empty',
  USER_COUNTRY_CODE_EMPTY_ERROR: 'Country code cannot be empty',
  USER_COUNTRY_CODE_INVALID_ERROR: 'Invalid country code: %s',
  USER_DETAILS_EMPTY_ERROR: 'User details cannot be empty',
  USER_EMAIL_ADDRESS_INVALID_ERROR: 'Invalid email: %s',
  USER_EMAIL_ALREADY_EXISTS_ERROR: 'Email already exists',
  USER_EMAIL_EMPTY_ERROR: 'Email cannot be empty',
  USER_NAME_EMPTY_ERROR: 'Name cannot be empty',
  USER_NOT_FOUND_BY_IDENTIFIER: 'User not found for the given identifier: %s',
  USER_PHONE_NUMBER_ALREADY_EXISTS_ERROR: 'Phone number already exists',
  USER_PHONE_NUMBER_EMPTY_ERROR: 'Phone number cannot be empty',
  USER_PHONE_NUMBER_INVALID_ERROR: 'Invalid phone number: %s for country code: %s',
  /** Spring Security BadCredentialsException message (GlobalExceptionHandler → 401). */
  BAD_CREDENTIALS: 'Bad credentials',
  /** JwtAuthenticationEntryPoint body when no token is sent (PrintWriter.println adds \n). */
  ACCESS_DENIED: 'Access Denied !! Full authentication is required to access this resource\n',
  /** AccountController#sendBankStatement literal. */
  STATEMENT_SENT: '{"message": "Bank statement sent to your email."}',
} as const;

/** Minimal String.format for %s / %d placeholders. */
export function format(template: string, ...args: Array<string | number>): string {
  let i = 0;
  return template.replace(/%[sd]/g, () => String(args[i++] ?? ''));
}
