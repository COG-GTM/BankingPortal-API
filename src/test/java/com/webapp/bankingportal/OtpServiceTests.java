package com.webapp.bankingportal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.webapp.bankingportal.entity.OtpInfo;
import com.webapp.bankingportal.exception.InvalidOtpException;
import com.webapp.bankingportal.exception.OtpRetryLimitExceededException;
import com.webapp.bankingportal.repository.OtpInfoRepository;
import com.webapp.bankingportal.service.EmailService;
import com.webapp.bankingportal.service.OtpServiceImpl;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.ValidationUtil;

import lombok.val;

public class OtpServiceTests {

    private static final String ACCOUNT_NUMBER = "123456";

    private final AtomicReference<OtpInfo> storedOtp = new AtomicReference<>();

    private OtpInfoRepository otpInfoRepository;
    private OtpServiceImpl otpService;

    @BeforeEach
    public void setUp() {
        otpInfoRepository = mock(OtpInfoRepository.class);
        val validationUtil = mock(ValidationUtil.class);
        when(validationUtil.doesAccountExist(anyString())).thenReturn(true);

        stubRepositoryWithSingleRow();

        otpService = new OtpServiceImpl(
                new ConcurrentMapCacheManager("otpAttempts"),
                mock(EmailService.class),
                otpInfoRepository,
                validationUtil);
    }

    private void stubRepositoryWithSingleRow() {
        when(otpInfoRepository.save(any(OtpInfo.class))).thenAnswer(invocation -> {
            OtpInfo otpInfo = invocation.getArgument(0);
            otpInfo.setId(1L);
            storedOtp.set(otpInfo);
            return otpInfo;
        });

        when(otpInfoRepository.findByAccountNumber(anyString())).thenAnswer(invocation -> {
            val otpInfo = storedOtp.get();
            if (otpInfo == null || !otpInfo.getAccountNumber().equals(invocation.getArgument(0))) {
                return null;
            }
            val copy = new OtpInfo(otpInfo.getAccountNumber(), otpInfo.getOtp(), otpInfo.getGeneratedAt());
            copy.setId(otpInfo.getId());
            copy.setFailedAttempts(otpInfo.getFailedAttempts());
            return copy;
        });

        when(otpInfoRepository.reserveVerificationAttempt(anyLong(), anyInt())).thenAnswer(invocation -> {
            val otpInfo = storedOtp.get();
            int maxAttempts = invocation.getArgument(1);
            if (otpInfo == null || otpInfo.getFailedAttempts() >= maxAttempts) {
                return 0;
            }
            otpInfo.setFailedAttempts(otpInfo.getFailedAttempts() + 1);
            return 1;
        });

        when(otpInfoRepository.consumeById(anyLong()))
                .thenAnswer(invocation -> storedOtp.getAndSet(null) == null ? 0 : 1);

        doAnswer(invocation -> {
            storedOtp.set(null);
            return null;
        }).when(otpInfoRepository).delete(any(OtpInfo.class));
    }

    private static String wrongOtp(String otp) {
        return otp.equals("111111") ? "222222" : "111111";
    }

    @Test
    public void test_generated_otp_is_six_digits() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);
        Assertions.assertTrue(otp.matches("\\d{6}"), otp);
    }

    @Test
    public void test_valid_otp_cannot_be_replayed() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);

        Assertions.assertTrue(otpService.validateOTP(ACCOUNT_NUMBER, otp));
        Assertions.assertNull(storedOtp.get(), "OTP must be consumed after successful verification");

        val ex = Assertions.assertThrows(InvalidOtpException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, otp));
        Assertions.assertEquals(ApiMessages.OTP_INVALID_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    public void test_concurrent_verification_only_one_request_consumes_otp() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);

        // Simulates a parallel request consuming the OTP between our lookup and our consume.
        when(otpInfoRepository.consumeById(anyLong())).thenReturn(0);

        Assertions.assertThrows(InvalidOtpException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, otp));
    }

    @Test
    public void test_verification_attempts_are_limited() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);

        for (int i = 0; i < OtpServiceImpl.OTP_VERIFICATION_ATTEMPTS_LIMIT; i++) {
            Assertions.assertThrows(InvalidOtpException.class,
                    () -> otpService.validateOTP(ACCOUNT_NUMBER, wrongOtp(otp)));
        }

        val ex = Assertions.assertThrows(OtpRetryLimitExceededException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, otp));
        Assertions.assertEquals(ApiMessages.OTP_VERIFICATION_LIMIT_EXCEEDED.getMessage(), ex.getMessage());
        Assertions.assertNull(storedOtp.get(), "Locked-out OTP must be invalidated");

        Assertions.assertThrows(InvalidOtpException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, otp));
    }

    @Test
    public void test_resending_otp_does_not_reset_verification_attempts() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);

        for (int i = 0; i < OtpServiceImpl.OTP_VERIFICATION_ATTEMPTS_LIMIT - 1; i++) {
            Assertions.assertThrows(InvalidOtpException.class,
                    () -> otpService.validateOTP(ACCOUNT_NUMBER, wrongOtp(otp)));
        }

        Assertions.assertEquals(otp, otpService.generateOTP(ACCOUNT_NUMBER));

        Assertions.assertThrows(InvalidOtpException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, wrongOtp(otp)));
        Assertions.assertThrows(OtpRetryLimitExceededException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, otp));
    }

    @Test
    public void test_successful_verification_within_attempt_limit() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);

        for (int i = 0; i < OtpServiceImpl.OTP_VERIFICATION_ATTEMPTS_LIMIT - 1; i++) {
            Assertions.assertThrows(InvalidOtpException.class,
                    () -> otpService.validateOTP(ACCOUNT_NUMBER, wrongOtp(otp)));
        }

        Assertions.assertTrue(otpService.validateOTP(ACCOUNT_NUMBER, otp));
    }

    @Test
    public void test_expired_otp_is_rejected_and_deleted() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);
        storedOtp.get().setGeneratedAt(
                LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_EXPIRY_MINUTES + 1));

        Assertions.assertFalse(otpService.validateOTP(ACCOUNT_NUMBER, otp));
        Assertions.assertNull(storedOtp.get());
        verify(otpInfoRepository, never()).consumeById(anyLong());
    }

    @Test
    public void test_otp_is_compared_without_querying_by_otp_value() {
        val otp = otpService.generateOTP(ACCOUNT_NUMBER);

        Assertions.assertThrows(InvalidOtpException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, otp.substring(0, 5)));
        Assertions.assertThrows(InvalidOtpException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, otp + "0"));
        Assertions.assertThrows(InvalidOtpException.class,
                () -> otpService.validateOTP(ACCOUNT_NUMBER, null));

        verify(otpInfoRepository, never()).consumeById(anyLong());
        Assertions.assertNotNull(storedOtp.get());
    }
}
