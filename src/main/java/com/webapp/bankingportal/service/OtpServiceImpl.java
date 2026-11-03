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
    public static final int OTP_VERIFICATION_ATTEMPTS_LIMIT = 5;

    static final String OTP_ATTEMPTS_CACHE = "otpAttempts";
    static final String OTP_LIMIT_REACHED_TIME_CACHE = "otpLimitReachedTime";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CacheManager cacheManager;
    private final EmailService emailService;
    private final OtpInfoRepository otpInfoRepository;
    private final ValidationUtil validationUtil;

    @Override
    public String generateOTP(String accountNumber) {
        if (!validationUtil.doesAccountExist(accountNumber)) {
            throw new AccountDoesNotExistException(ApiMessages.ACCOUNT_NOT_FOUND.getMessage());
        }

        validateOtpWithinRetryLimit(accountNumber);
        incrementOtpAttempts(accountNumber);

        val existingOtpInfo = otpInfoRepository.findByAccountNumber(accountNumber);
        if (existingOtpInfo == null || isOtpExpired(existingOtpInfo)) {
            return generateNewOTP(accountNumber);
        }

        return existingOtpInfo.getOtp();
    }

    private void validateOtpWithinRetryLimit(String accountNumber) {
        if (!isOtpRetryLimitExceeded(accountNumber)) {
            return;
        }

        val now = LocalDateTime.now();
        var limitReachedTime = getOtpLimitReachedTime(accountNumber);
        if (limitReachedTime == null) {
            limitReachedTime = now;
            putInCache(OTP_LIMIT_REACHED_TIME_CACHE, accountNumber, limitReachedTime);
        }

        val waitingMinutes = OTP_RESET_WAITING_TIME_MINUTES - limitReachedTime.until(now, ChronoUnit.MINUTES);

        throw new OtpRetryLimitExceededException(
                String.format(ApiMessages.OTP_GENERATION_LIMIT_EXCEEDED.getMessage(), waitingMinutes));
    }

    private boolean isOtpRetryLimitExceeded(String accountNumber) {
        if (getOtpAttempts(accountNumber) < OTP_ATTEMPTS_LIMIT) {
            return false;
        }

        if (isOtpResetWaitingTimeExceeded(accountNumber)) {
            resetOtpAttempts(accountNumber);
            return false;
        }

        return true;
    }

    private boolean isOtpResetWaitingTimeExceeded(String accountNumber) {
        val limitReachedTime = getOtpLimitReachedTime(accountNumber);
        return limitReachedTime != null
                && limitReachedTime.isBefore(LocalDateTime.now().minusMinutes(OTP_RESET_WAITING_TIME_MINUTES));
    }

    private void incrementOtpAttempts(String accountNumber) {
        putInCache(OTP_ATTEMPTS_CACHE, accountNumber, getOtpAttempts(accountNumber) + 1);
    }

    private void resetOtpAttempts(String accountNumber) {
        val limitReachedTimeCache = cacheManager.getCache(OTP_LIMIT_REACHED_TIME_CACHE);
        if (limitReachedTimeCache != null) {
            limitReachedTimeCache.evict(accountNumber);
        }
        putInCache(OTP_ATTEMPTS_CACHE, accountNumber, 0);
    }

    private int getOtpAttempts(String accountNumber) {
        val cache = cacheManager.getCache(OTP_ATTEMPTS_CACHE);
        if (cache == null) {
            return 0;
        }

        val value = cache.get(accountNumber, Integer.class);
        return value == null ? 0 : value;
    }

    private LocalDateTime getOtpLimitReachedTime(String accountNumber) {
        val cache = cacheManager.getCache(OTP_LIMIT_REACHED_TIME_CACHE);
        return cache == null ? null : cache.get(accountNumber, LocalDateTime.class);
    }

    private void putInCache(String cacheName, String key, Object value) {
        val cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
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
        val otpInfo = otpInfoRepository.findByAccountNumberForUpdate(accountNumber);
        if (otpInfo == null) {
            throw new InvalidOtpException(ApiMessages.OTP_INVALID_ERROR.getMessage());
        }

        if (isOtpExpired(otpInfo)) {
            return false;
        }

        if (!otpMatches(otpInfo.getOtp(), otp)) {
            registerFailedAttempt(otpInfo);
            throw new InvalidOtpException(ApiMessages.OTP_INVALID_ERROR.getMessage());
        }

        otpInfoRepository.delete(otpInfo);
        return true;
    }

    private static boolean otpMatches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private void registerFailedAttempt(OtpInfo otpInfo) {
        val failedAttempts = otpInfo.getFailedAttempts() + 1;
        if (failedAttempts >= OTP_VERIFICATION_ATTEMPTS_LIMIT) {
            otpInfoRepository.delete(otpInfo);
            return;
        }

        otpInfo.setFailedAttempts(failedAttempts);
        otpInfoRepository.save(otpInfo);
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
