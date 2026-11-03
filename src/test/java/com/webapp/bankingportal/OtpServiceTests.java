package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import com.webapp.bankingportal.entity.OtpInfo;
import com.webapp.bankingportal.repository.OtpInfoRepository;
import com.webapp.bankingportal.service.EmailService;
import com.webapp.bankingportal.service.OtpServiceImpl;
import com.webapp.bankingportal.util.ValidationUtil;

@ExtendWith(MockitoExtension.class)
class OtpServiceTests {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private EmailService emailService;

    @Mock
    private OtpInfoRepository otpInfoRepository;

    @Mock
    private ValidationUtil validationUtil;

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(cacheManager, emailService, otpInfoRepository, validationUtil);
    }

    @Test
    void generateOTP_ReturnsSixDigitCodeAndPersistsIt() {
        when(validationUtil.doesAccountExist("123456")).thenReturn(true);

        String otp = otpService.generateOTP("123456");

        assertTrue(otp.matches("[1-9]\\d{5}"), "OTP must be a 6-digit code: " + otp);
        ArgumentCaptor<OtpInfo> saved = ArgumentCaptor.forClass(OtpInfo.class);
        verify(otpInfoRepository).save(saved.capture());
        assertEquals("123456", saved.getValue().getAccountNumber());
        assertEquals(otp, saved.getValue().getOtp());
    }

    @Test
    void generateOTP_ProducesVaryingCodes() {
        when(validationUtil.doesAccountExist(any())).thenReturn(true);

        HashSet<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(otpService.generateOTP("ACC" + i));
        }

        assertTrue(codes.size() > 40, "Expected mostly distinct OTPs, got " + codes.size());
    }

    @Test
    void otpGenerator_UsesSecureRandom() {
        boolean usesSecureRandom = Arrays.stream(OtpServiceImpl.class.getDeclaredFields())
                .filter(f -> Random.class.isAssignableFrom(f.getType()))
                .allMatch(f -> f.getType() == SecureRandom.class && Modifier.isStatic(f.getModifiers()));
        boolean hasRandomField = Arrays.stream(OtpServiceImpl.class.getDeclaredFields())
                .anyMatch(f -> f.getType() == SecureRandom.class);

        assertTrue(hasRandomField && usesSecureRandom, "OTPs must be generated with SecureRandom");
    }
}
