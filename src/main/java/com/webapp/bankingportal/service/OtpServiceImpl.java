package com.webapp.bankingportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webapp.bankingportal.entity.OtpInfo;
import com.webapp.bankingportal.exception.AccountDoesNotExistException;
import com.webapp.bankingportal.exception.InvalidOtpException;
import com.webapp.bankingportal.exception.OtpRetryLimitExceededException;
import com.webapp.bankingportal.repository.OtpInfoRepository;
import com.webapp.bankingportal.util.ValidationUtil;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    public static final int OTP_ATTEMPTS_LIMIT = 3;
    public static final int OTP_EXPIRY_MINUTES = 5;
    public static final int OTP_RESET_WAITING_TIME_MINUTES = 10;
    public static final int OTP_RETRY_LIMIT_WINDOW_MINUTES = 15;
    public static final int OTP_MAX_VERIFICATION_ATTEMPTS = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CacheManager cacheManager;
    private final EmailService emailService;
    private final OtpInfoRepository otpInfoRepository;
    private final ValidationUtil validationUtil;

    private LocalDateTime otpLimitReachedTime = null;

    @Override
    public String generateOTP(String accountNumber) {
        if (!validationUtil.doesAccountExist(accountNumber)) {
            throw new AccountDoesNotExistException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        val existingOtpInfo = otpInfoRepository.findByAccountNumber(accountNumber);
        if (existingOtpInfo == null) {
            incrementOtpAttempts(accountNumber);
            return generateNewOTP(accountNumber);
        }

        validateOtpWithinRetryLimit(existingOtpInfo);

        if (isOtpExpired(existingOtpInfo)) {
            return generateNewOTP(accountNumber);
        }

        // Existing OTP is not expired
        existingOtpInfo.setGeneratedAt(LocalDateTime.now());
        incrementOtpAttempts(accountNumber);

        return existingOtpInfo.getOtp();
    }

    private void validateOtpWithinRetryLimit(OtpInfo otpInfo) {
        if (!isOtpRetryLimitExceeded(otpInfo)) {
            return;
        }

        val now = LocalDateTime.now();

        if (otpLimitReachedTime == null) {
            otpLimitReachedTime = now;
        }

        val waitingMinutes = OTP_RESET_WAITING_TIME_MINUTES - otpLimitReachedTime.until(now, ChronoUnit.MINUTES);

        throw new OtpRetryLimitExceededException(
                String.format(ApiMessages.OTP_GENERATION_LIMIT_EXCEEDED.getMessage(), waitingMinutes));
    }

    private boolean isOtpRetryLimitExceeded(OtpInfo otpInfo) {
        val attempts = getOtpAttempts(otpInfo.getAccountNumber());
        if (attempts < OTP_ATTEMPTS_LIMIT) {
            return false;
        }

        if (isOtpResetWaitingTimeExceeded()) {
            resetOtpAttempts(otpInfo.getAccountNumber());
            return false;
        }

        val now = LocalDateTime.now();

        return otpInfo.getGeneratedAt().isAfter(now.minusMinutes(OTP_RETRY_LIMIT_WINDOW_MINUTES));
    }

    private boolean isOtpResetWaitingTimeExceeded() {
        val now = LocalDateTime.now();
        return otpLimitReachedTime != null
                && otpLimitReachedTime.isBefore(now.minusMinutes(OTP_RESET_WAITING_TIME_MINUTES));
    }

    private void incrementOtpAttempts(String accountNumber) {
        if (!validationUtil.doesAccountExist(accountNumber)) {
            throw new AccountDoesNotExistException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        val cache = cacheManager.getCache("otpAttempts");
        if (cache != null) {
            cache.put(accountNumber, getOtpAttempts(accountNumber) + 1);
        }
    }

    private void resetOtpAttempts(String accountNumber) {
        otpLimitReachedTime = null;
        val cache = cacheManager.getCache("otpAttempts");
        if (cache != null) {
            cache.put(accountNumber, 0);
        }
    }

    private int getOtpAttempts(String accountNumber) {
        var otpAttempts = 0;
        val cache = cacheManager.getCache("otpAttempts");
        if (cache == null) {
            return otpAttempts;
        }

        val value = cache.get(accountNumber, Integer.class);
        if (value != null) {
            otpAttempts = value;
        }

        return otpAttempts;
    }

    private String generateNewOTP(String accountNumber) {
        val otpValue = 100_000 + SECURE_RANDOM.nextInt(900_000);
        val otp = String.valueOf(otpValue);

        otpInfoRepository.save(new OtpInfo(accountNumber, otp, LocalDateTime.now()));

        return otp;
    }

    @Override
    public CompletableFuture<Void> sendOTPByEmail(String email, String name, String accountNumber, String otp) {
        val emailText = emailService.getOtpLoginEmailTemplate(name, "xxx" + accountNumber.substring(3), otp);
        return emailService.sendEmail(email, ApiMessages.EMAIL_SUBJECT_OTP.getMessage(), emailText);
    }

    @Override
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public boolean validateOTP(String accountNumber, String otp) {
        val otpInfo = otpInfoRepository.findByAccountNumber(accountNumber);
        if (otpInfo == null || otp == null) {
            throw new InvalidOtpException(ApiMessages.OTP_INVALID_ERROR.getMessage());
        }

        if (isOtpExpired(otpInfo)) {
            return false;
        }

        // Atomically reserve an attempt so concurrent guesses cannot exceed the limit.
        val reserved = otpInfoRepository.incrementVerificationAttempts(
                otpInfo.getId(), OTP_MAX_VERIFICATION_ATTEMPTS);
        if (reserved == 0) {
            otpInfoRepository.deleteOtpInfoById(otpInfo.getId());
            throw new InvalidOtpException(ApiMessages.OTP_INVALID_ERROR.getMessage());
        }

        if (!otpMatches(otpInfo.getOtp(), otp)) {
            if (otpInfo.getVerificationAttempts() + 1 >= OTP_MAX_VERIFICATION_ATTEMPTS) {
                otpInfoRepository.deleteOtpInfoById(otpInfo.getId());
            }
            throw new InvalidOtpException(ApiMessages.OTP_INVALID_ERROR.getMessage());
        }

        // Single use: consume the code on successful verification.
        if (otpInfoRepository.deleteOtpInfoById(otpInfo.getId()) == 0) {
            throw new InvalidOtpException(ApiMessages.OTP_INVALID_ERROR.getMessage());
        }

        return true;
    }

    private static boolean otpMatches(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isOtpExpired(OtpInfo otpInfo) {
        val now = LocalDateTime.now();
        val generatedAt = otpInfo.getGeneratedAt();
        val expired = generatedAt.isBefore(now.minusMinutes(OTP_EXPIRY_MINUTES));
        if (expired) {
            otpInfoRepository.delete(otpInfo);
        }

        return expired;
    }

}
