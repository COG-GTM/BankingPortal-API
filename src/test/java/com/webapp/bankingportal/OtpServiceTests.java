package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;

import com.webapp.bankingportal.config.CacheConfig;
import com.webapp.bankingportal.entity.OtpInfo;
import com.webapp.bankingportal.exception.OtpRetryLimitExceededException;
import com.webapp.bankingportal.repository.OtpInfoRepository;
import com.webapp.bankingportal.service.EmailService;
import com.webapp.bankingportal.service.OtpServiceImpl;
import com.webapp.bankingportal.util.ValidationUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTests {

    private static final String ACCOUNT_A = "aaa111";
    private static final String ACCOUNT_B = "bbb222";

    @Mock
    private EmailService emailService;

    @Mock
    private OtpInfoRepository otpInfoRepository;

    @Mock
    private ValidationUtil validationUtil;

    private CacheManager cacheManager;
    private OtpServiceImpl otpService;
    private final Map<String, OtpInfo> otps = new HashMap<>();

    @BeforeEach
    void setUp() {
        cacheManager = new CacheConfig().cacheManager();
        otpService = new OtpServiceImpl(cacheManager, emailService, otpInfoRepository, validationUtil);

        when(validationUtil.doesAccountExist(anyString())).thenReturn(true);
        when(otpInfoRepository.findByAccountNumber(anyString()))
                .thenAnswer(inv -> otps.get(inv.getArgument(0, String.class)));
        when(otpInfoRepository.save(org.mockito.ArgumentMatchers.any(OtpInfo.class)))
                .thenAnswer(inv -> {
                    OtpInfo info = inv.getArgument(0);
                    otps.put(info.getAccountNumber(), info);
                    return info;
                });
    }

    private void exhaustAttempts(String accountNumber) {
        for (int i = 0; i < OtpServiceImpl.OTP_ATTEMPTS_LIMIT; i++) {
            otpService.generateOTP(accountNumber);
        }
    }

    private LocalDateTime limitReachedTime(String accountNumber) {
        return cacheManager.getCache(OtpServiceImpl.OTP_LIMIT_REACHED_TIME_CACHE)
                .get(accountNumber, LocalDateTime.class);
    }

    private void setLimitReachedTime(String accountNumber, LocalDateTime time) {
        cacheManager.getCache(OtpServiceImpl.OTP_LIMIT_REACHED_TIME_CACHE).put(accountNumber, time);
    }

    @Test
    void limitReachedTimeIsTrackedPerAccount() {
        exhaustAttempts(ACCOUNT_A);
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT_A));
        LocalDateTime aTime = limitReachedTime(ACCOUNT_A);
        assertNotNull(aTime);
        assertNull(limitReachedTime(ACCOUNT_B));

        // Account A tripped the limit 5 minutes before account B.
        setLimitReachedTime(ACCOUNT_A, aTime.minusMinutes(5));

        exhaustAttempts(ACCOUNT_B);
        OtpRetryLimitExceededException ex = assertThrows(OtpRetryLimitExceededException.class,
                () -> otpService.generateOTP(ACCOUNT_B));
        assertEquals(String.format(
                com.webapp.bankingportal.util.ApiMessages.OTP_GENERATION_LIMIT_EXCEEDED.getMessage(),
                OtpServiceImpl.OTP_RESET_WAITING_TIME_MINUTES), ex.getMessage());
        assertNotNull(limitReachedTime(ACCOUNT_B));
        assertEquals(aTime.minusMinutes(5), limitReachedTime(ACCOUNT_A));
    }

    @Test
    void anotherAccountsExpiredWaitDoesNotUnblockAccount() {
        exhaustAttempts(ACCOUNT_A);
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT_A));

        exhaustAttempts(ACCOUNT_B);
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT_B));

        // A's wait has elapsed (11 minutes ago); B tripped 5 minutes after A, so B is still blocked.
        LocalDateTime aTime = LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_RESET_WAITING_TIME_MINUTES + 1);
        setLimitReachedTime(ACCOUNT_A, aTime);
        setLimitReachedTime(ACCOUNT_B, aTime.plusMinutes(5));

        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT_B));
        assertDoesNotThrow(() -> otpService.generateOTP(ACCOUNT_A));
        assertNull(limitReachedTime(ACCOUNT_A));
    }

    @Test
    void unblockingOneAccountDoesNotClearAnotherAccountsTimer() {
        exhaustAttempts(ACCOUNT_A);
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT_A));
        exhaustAttempts(ACCOUNT_B);
        assertThrows(OtpRetryLimitExceededException.class, () -> otpService.generateOTP(ACCOUNT_B));

        LocalDateTime bTime = LocalDateTime.now().minusMinutes(3);
        setLimitReachedTime(ACCOUNT_A,
                LocalDateTime.now().minusMinutes(OtpServiceImpl.OTP_RESET_WAITING_TIME_MINUTES + 1));
        setLimitReachedTime(ACCOUNT_B, bTime);

        assertDoesNotThrow(() -> otpService.generateOTP(ACCOUNT_A));

        assertEquals(bTime, limitReachedTime(ACCOUNT_B));
        OtpRetryLimitExceededException ex = assertThrows(OtpRetryLimitExceededException.class,
                () -> otpService.generateOTP(ACCOUNT_B));
        assertEquals(String.format(
                com.webapp.bankingportal.util.ApiMessages.OTP_GENERATION_LIMIT_EXCEEDED.getMessage(),
                OtpServiceImpl.OTP_RESET_WAITING_TIME_MINUTES - 3), ex.getMessage());
    }
}
