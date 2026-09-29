package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.UserInvalidException;
import com.webapp.bankingportal.security.CredentialPolicy;
import com.webapp.bankingportal.util.ApiMessages;

import lombok.val;

public class CredentialPolicyTests {

    private final CredentialPolicy policy = new CredentialPolicy(90, 180);

    private static User owner() {
        val user = new User();
        user.setName("Alice Johnson");
        user.setEmail("alice.j@example.com");
        user.setPhoneNumber("+14155557391");
        return user;
    }

    @ParameterizedTest
    @ValueSource(strings = { "Password1!", "P@ssw0rd123!", "Qwerty123!", "Summer2024!", "!!Welcome99", "Letmein#1",
            "0nestopBank!" })
    public void test_common_or_breached_passwords_are_rejected(String password) {
        val ex = assertThrows(UserInvalidException.class, () -> policy.validatePassword(password, owner()));
        assertEquals(ApiMessages.PASSWORD_BREACHED_ERROR.getMessage(), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "Johnson#2024x", "xX!alice99Q", "Al1ce!Rocks7" })
    public void test_passwords_containing_personal_info_are_rejected(String password) {
        val ex = assertThrows(UserInvalidException.class, () -> policy.validatePassword(password, owner()));
        assertEquals(ApiMessages.PASSWORD_CONTAINS_PERSONAL_INFO_ERROR.getMessage(), ex.getMessage());
    }

    @Test
    public void test_existing_composition_rules_still_apply() {
        val ex = assertThrows(UserInvalidException.class, () -> policy.validatePassword("Ab1!", owner()));
        assertEquals(ApiMessages.PASSWORD_TOO_SHORT_ERROR.getMessage(), ex.getMessage());

        assertThrows(UserInvalidException.class, () -> policy.validatePassword("alllowercase1!", owner()));
    }

    @Test
    public void test_empty_password_is_rejected() {
        val ex = assertThrows(UserInvalidException.class, () -> policy.validatePassword(null, owner()));
        assertEquals(ApiMessages.PASSWORD_EMPTY_ERROR.getMessage(), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "Tr0ub4dor&Horse", "vX9#kLm2!qPz", "Correct-Horse-7-Staple" })
    public void test_strong_passwords_are_accepted(String password) {
        assertDoesNotThrow(() -> policy.validatePassword(password, owner()));
        assertDoesNotThrow(() -> policy.validatePassword(password, null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0000", "1111", "1234", "4321", "6789", "1212", "6969", "1122", "1990", "2024", "2580",
            "1004" })
    public void test_weak_pins_are_rejected(String pin) {
        assertTrue(CredentialPolicy.isWeakPin(pin));
        val ex = assertThrows(InvalidPinException.class, () -> policy.validatePin(pin, owner()));
        assertEquals(ApiMessages.PIN_TOO_WEAK_ERROR.getMessage(), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "12", "12345", "12a4", "" })
    public void test_malformed_pins_are_rejected(String pin) {
        assertThrows(InvalidPinException.class, () -> policy.validatePin(pin, owner()));
    }

    @Test
    public void test_pin_matching_phone_suffix_is_rejected() {
        val ex = assertThrows(InvalidPinException.class, () -> policy.validatePin("7391", owner()));
        assertEquals(ApiMessages.PIN_MATCHES_PHONE_ERROR.getMessage(), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "7351", "4830", "9175", "3062" })
    public void test_reasonable_pins_are_accepted(String pin) {
        assertFalse(CredentialPolicy.isWeakPin(pin));
        assertDoesNotThrow(() -> policy.validatePin(pin, owner()));
    }

    @Test
    public void test_rotation_due_uses_configured_max_age() {
        val now = LocalDateTime.now();

        assertTrue(policy.isRotationDue(null, false));
        assertFalse(policy.isRotationDue(now.minusDays(89), false));
        assertTrue(policy.isRotationDue(now.minusDays(91), false));

        assertFalse(policy.isRotationDue(now.minusDays(179), true));
        assertTrue(policy.isRotationDue(now.minusDays(181), true));
    }
}
