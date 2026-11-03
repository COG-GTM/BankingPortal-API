package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.webapp.bankingportal.entity.OtpInfo;
import com.webapp.bankingportal.exception.InvalidOtpException;
import com.webapp.bankingportal.exception.OtpRetryLimitExceededException;
import com.webapp.bankingportal.repository.OtpInfoRepository;
import com.webapp.bankingportal.service.EmailService;
import com.webapp.bankingportal.service.OtpServiceImpl;
import com.webapp.bankingportal.util.ValidationUtil;

@ExtendWith(MockitoExtension.class)
class OtpServiceTests {

    private static final String ACCOUNT = "abc123";
    private static final String OTHER_ACCOUNT = "def456";

    @Mock
    private EmailService emailService;

    @Mock
    private OtpInfoRepository otpInfoRepository;

    @Mock
    private ValidationUtil validationUtil;

    private final Map<String, OtpInfo> store = new HashMap<>();
    private CacheManager cacheManager;
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager("otpAttempts", "otpLimitReachedTime");
        otpService = new OtpServiceImpl(cacheManager, emailService, otpInfoRepository, validationUtil);

        lenient().when(validationUtil.doesAccountExist(anyString())).thenReturn(true);
        lenient().when(otpInfoRepository.findByAccountNumber(anyString()))
                .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        lenient().when(otpInfoRepository.findByAccountNumberForUpdate(anyString()))
                .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        lenient().when(otpInfoRepository.save(any(OtpInfo.class))).thenAnswer(inv -> {
            OtpInfo info = inv.getArgument(0);
            store.put(info.getAccountNumber(), info);
            return info;
        });
        lenient().doAnswer(inv -> store.remove(inv.<OtpInfo>getArgument(0).getAccountNumber()))
                .when(otpInfoRepository).delete(any(OtpInfo.class));
    }

    private static String wrongOtp(String otp) {
        return otp.equals("111111") ? "222222" : "111111";
    }

    @Test
    void validOtp_isConsumed_andCannotBeReplayed() {
        String otp = otpService.generateOTP(ACCOUNT);

        assertTrue(otpService.validateOTP(ACCOUNT, otp));
        assertNull(store.get(ACCOUNT));
        assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, otp));
    }

    @Test
    void otp_isInvalidated_afterMaxFailedVerificationAttempts() {
        String otp = otpService.generateOTP(ACCOUNT);
        String wrong = wrongOtp(otp);

        for (int i = 1; i < OtpServiceImpl.OTP_VERIFICATION_ATTEMPTS_LIMIT; i++) {
            assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, wrong));
            assertEquals(i, store.get(ACCOUNT).getFailedAttempts());
        }

        assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, wrong));
        assertNull(store.get(ACCOUNT));

        assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, otp));
    }

    @Test
    void correctOtp_isAccepted_beforeFailedAttemptLimitIsReached() {
        String otp = otpService.generateOTP(ACCOUNT);
        String wrong = wrongOtp(otp);

        for (int i = 1; i < OtpServiceImpl.OTP_VERIFICATION_ATTEMPTS_LIMIT; i++) {
            assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, wrong));
        }

        assertTrue(otpService.validateOTP(ACCOUNT, otp));
    }

    @Test
    void generationLimit_isEnforced_evenAfterOtpWasConsumedOrInvalidated() {
        for (int i = 0; i < OtpServiceImpl.OTP_ATTEMPTS_LIMIT; i++) {
            String otp = otpService.generateOTP(ACCOUNT);
            assertTrue(otpService.validateOTP(ACCOUNT, otp));
        }

        assertNull(store.get(ACCOUNT));
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT));
    }

    @Test
    void generationLimitReset_isTrackedPerAccount() {
        for (int i = 0; i < OtpServiceImpl.OTP_ATTEMPTS_LIMIT; i++) {
            otpService.generateOTP(ACCOUNT);
            otpService.generateOTP(OTHER_ACCOUNT);
        }
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT));
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(OTHER_ACCOUNT));

        cacheManager.getCache("otpLimitReachedTime").put(ACCOUNT,
                LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_RESET_WAITING_TIME_MINUTES + 1));

        assertNotNull(otpService.generateOTP(ACCOUNT));
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(OTHER_ACCOUNT));
    }

    @Test
    void resendingOtp_doesNotExtendItsExpiry() {
        String otp = otpService.generateOTP(ACCOUNT);
        LocalDateTime originalGeneratedAt = LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_EXPIRY_MINUTES - 1);
        store.get(ACCOUNT).setGeneratedAt(originalGeneratedAt);

        assertEquals(otp, otpService.generateOTP(ACCOUNT));
        assertEquals(originalGeneratedAt, store.get(ACCOUNT).getGeneratedAt());
    }

    @Test
    void expiredOtp_isRejected_andDeleted() {
        String otp = otpService.generateOTP(ACCOUNT);
        store.get(ACCOUNT).setGeneratedAt(LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_EXPIRY_MINUTES + 1));

        assertFalse(otpService.validateOTP(ACCOUNT, otp));
        assertNull(store.get(ACCOUNT));
    }

    @Test
    void malformedOtp_isRejected_andCountsAsFailedAttempt() {
        otpService.generateOTP(ACCOUNT);

        assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, null));
        assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, "1"));
        assertThrows(InvalidOtpException.class, () -> otpService.validateOTP(ACCOUNT, "1234567"));
        assertEquals(3, store.get(ACCOUNT).getFailedAttempts());
    }

    @Test
    void generatedOtp_isSixDigits() {
        String otp = otpService.generateOTP(ACCOUNT);

        assertTrue(otp.matches("\\d{6}"));
    }
}
