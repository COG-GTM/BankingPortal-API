package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import com.webapp.bankingportal.entity.OtpInfo;
import com.webapp.bankingportal.exception.AccountDoesNotExistException;
import com.webapp.bankingportal.exception.InvalidOtpException;
import com.webapp.bankingportal.exception.OtpRetryLimitExceededException;
import com.webapp.bankingportal.repository.OtpInfoRepository;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.ValidationUtil;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplUnitTests {

    private static final String ACCOUNT_NUMBER = "abc123";
    private static final String CACHE_NAME = "otpAttempts";

    @Mock
    private EmailService emailService;

    @Mock
    private OtpInfoRepository otpInfoRepository;

    @Mock
    private ValidationUtil validationUtil;

    private CacheManager cacheManager;

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(CACHE_NAME);
        otpService = new OtpServiceImpl(cacheManager, emailService, otpInfoRepository, validationUtil);
    }

    private Integer attempts() {
        return cacheManager.getCache(CACHE_NAME).get(ACCOUNT_NUMBER, Integer.class);
    }

    private void givenAttempts(int attempts) {
        cacheManager.getCache(CACHE_NAME).put(ACCOUNT_NUMBER, attempts);
    }

    @Test
    void generateOTP_rejectsUnknownAccount() {
        var ex = assertThrows(AccountDoesNotExistException.class, () -> otpService.generateOTP(ACCOUNT_NUMBER));
        assertEquals(ApiMessages.ACCOUNT_NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    void generateOTP_createsNewOtpWhenNoneExists() {
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);

        var otp = otpService.generateOTP(ACCOUNT_NUMBER);

        assertTrue(otp.matches("\\d{6}"));
        var captor = ArgumentCaptor.forClass(OtpInfo.class);
        verify(otpInfoRepository).save(captor.capture());
        assertEquals(ACCOUNT_NUMBER, captor.getValue().getAccountNumber());
        assertEquals(otp, captor.getValue().getOtp());
        assertEquals(1, attempts());
    }

    @Test
    void generateOTP_reusesUnexpiredOtpAndCountsAttempt() {
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);
        var existing = new OtpInfo(ACCOUNT_NUMBER, "654321", LocalDateTime.now().minusMinutes(1));
        when(otpInfoRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(existing);
        givenAttempts(1);

        var otp = otpService.generateOTP(ACCOUNT_NUMBER);

        assertEquals("654321", otp);
        assertEquals(2, attempts());
        assertTrue(existing.getGeneratedAt().isAfter(LocalDateTime.now().minusSeconds(5)));
        verify(otpInfoRepository, never()).save(any());
    }

    @Test
    void generateOTP_replacesExpiredOtp() {
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);
        var expired = new OtpInfo(ACCOUNT_NUMBER, "654321",
                LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_EXPIRY_MINUTES + 1));
        when(otpInfoRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(expired);

        var otp = otpService.generateOTP(ACCOUNT_NUMBER);

        assertNotEquals("654321", otp);
        verify(otpInfoRepository).delete(expired);
        verify(otpInfoRepository).save(any(OtpInfo.class));
    }

    @Test
    void generateOTP_throwsWhenRetryLimitExceeded() {
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);
        when(otpInfoRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(new OtpInfo(ACCOUNT_NUMBER, "654321", LocalDateTime.now()));
        givenAttempts(OtpServiceImpl.OTP_ATTEMPTS_LIMIT);

        var ex = assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT_NUMBER));

        assertEquals(String.format(ApiMessages.OTP_GENERATION_LIMIT_EXCEEDED.getMessage(),
                OtpServiceImpl.OTP_RESET_WAITING_TIME_MINUTES), ex.getMessage());
        assertEquals(OtpServiceImpl.OTP_ATTEMPTS_LIMIT, attempts());
    }

    @Test
    void generateOTP_allowsRetryOnceOutsideRetryWindow() {
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);
        var existing = new OtpInfo(ACCOUNT_NUMBER, "654321",
                LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_RETRY_LIMIT_WINDOW_MINUTES + 1));
        when(otpInfoRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(existing);
        givenAttempts(OtpServiceImpl.OTP_ATTEMPTS_LIMIT);

        var otp = otpService.generateOTP(ACCOUNT_NUMBER);

        assertTrue(otp.matches("\\d{6}"));
        verify(otpInfoRepository).delete(existing);
    }

    @Test
    void generateOTP_resetsAttemptsAfterWaitingTimeElapsed() {
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);
        when(otpInfoRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(new OtpInfo(ACCOUNT_NUMBER, "654321", LocalDateTime.now()));
        givenAttempts(OtpServiceImpl.OTP_ATTEMPTS_LIMIT);
        ReflectionTestUtils.setField(otpService, "otpLimitReachedTime",
                LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_RESET_WAITING_TIME_MINUTES + 1));

        var otp = otpService.generateOTP(ACCOUNT_NUMBER);

        assertEquals("654321", otp);
        assertEquals(1, attempts());
    }

    @Test
    void generateOTP_worksWithoutAttemptsCache() {
        var noCacheManager = mock(CacheManager.class);
        var service = new OtpServiceImpl(noCacheManager, emailService, otpInfoRepository, validationUtil);
        when(validationUtil.doesAccountExist(ACCOUNT_NUMBER)).thenReturn(true);
        when(otpInfoRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(new OtpInfo(ACCOUNT_NUMBER, "654321", LocalDateTime.now()));

        assertEquals("654321", service.generateOTP(ACCOUNT_NUMBER));
    }

    @Test
    void sendOTPByEmail_masksAccountNumberAndSendsEmail() {
        var future = CompletableFuture.<Void>completedFuture(null);
        when(emailService.getOtpLoginEmailTemplate("Jane", "xxx123", "111222")).thenReturn("<html/>");
        when(emailService.sendEmail("jane@example.com", ApiMessages.EMAIL_SUBJECT_OTP.getMessage(), "<html/>"))
                .thenReturn(future);

        var result = otpService.sendOTPByEmail("jane@example.com", "Jane", ACCOUNT_NUMBER, "111222");

        assertSame(future, result);
        verify(emailService).sendEmail(eq("jane@example.com"), eq(ApiMessages.EMAIL_SUBJECT_OTP.getMessage()),
                eq("<html/>"));
    }

    @Test
    void validateOTP_rejectsUnknownOtp() {
        var ex = assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT_NUMBER, "000000"));
        assertEquals(ApiMessages.OTP_INVALID_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    void validateOTP_acceptsFreshOtp() {
        when(otpInfoRepository.findByAccountNumberAndOtp(ACCOUNT_NUMBER, "111222"))
                .thenReturn(new OtpInfo(ACCOUNT_NUMBER, "111222", LocalDateTime.now()));

        assertTrue(otpService.validateOTP(ACCOUNT_NUMBER, "111222"));
        verify(otpInfoRepository, never()).delete(any());
    }

    @Test
    void validateOTP_rejectsAndDeletesExpiredOtp() {
        var expired = new OtpInfo(ACCOUNT_NUMBER, "111222",
                LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_EXPIRY_MINUTES + 1));
        when(otpInfoRepository.findByAccountNumberAndOtp(ACCOUNT_NUMBER, "111222")).thenReturn(expired);

        assertFalse(otpService.validateOTP(ACCOUNT_NUMBER, "111222"));
        verify(otpInfoRepository).delete(expired);
    }
}
