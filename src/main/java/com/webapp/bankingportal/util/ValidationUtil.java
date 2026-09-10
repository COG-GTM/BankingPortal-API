package com.webapp.bankingportal.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import com.webapp.bankingportal.dto.UpdateUserRequest;
import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.UserInvalidException;
import com.webapp.bankingportal.repository.UserRepository;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import lombok.RequiredArgsConstructor;
import lombok.val;

@Component
@RequiredArgsConstructor
public class ValidationUtil {

    public static final Logger log = LoggerFactory.getLogger(ValidationUtil.class);
    public static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    private final UserRepository userRepository;

    public static boolean isValidEmail(String identifier) {
        try {
            new InternetAddress(identifier).validate();
            return true;
        } catch (AddressException e) {
            log.warn("Invalid email address: {}", identifier);
        }

        return false;
    }

    public static boolean isValidAccountNumber(String identifier) {
        // Account number validation logic (e.g., length check)
        return identifier != null && identifier.length() == 6;
    }

    public static boolean isValidCountryCode(String countryCode) {
        if (!phoneNumberUtil.getSupportedRegions().contains(countryCode)) {
            return false;
        }

        return true;
    }

    public static boolean isValidPhoneNumber(String phoneNumber, String countryCode) {
        PhoneNumber parsedNumber = null;

        try {
            parsedNumber = phoneNumberUtil.parse(phoneNumber, countryCode);
        } catch (NumberParseException e) {
            throw new UserInvalidException(String.format(ApiMessages.USER_PHONE_NUMBER_INVALID_ERROR.getMessage(), phoneNumber, countryCode));
        }

        return phoneNumberUtil.isValidNumber(parsedNumber);
    }

    public static void validatePassword(String password) {
        if (password.length() < 8) {
            throw new UserInvalidException(ApiMessages.PASSWORD_TOO_SHORT_ERROR.getMessage());
        }

        if (password.length() >= 128) {
            throw new UserInvalidException(ApiMessages.PASSWORD_TOO_LONG_ERROR.getMessage());
        }

        if (password.matches(".*\\s.*")) {
            throw new UserInvalidException(ApiMessages.PASSWORD_CONTAINS_WHITESPACE_ERROR.getMessage());
        }

        val message = new StringBuilder();
        message.append("Password must contain at least ");

        var needsComma = false;
        if (!password.matches(".*[A-Z].*")) {
            message.append("one uppercase letter");
            needsComma = true;
        }

        if (!password.matches(".*[a-z].*")) {
            if (needsComma) {
                message.append(", ");
            }
            message.append("one lowercase letter");
            needsComma = true;
        }

        if (!password.matches(".*[0-9].*")) {
            if (needsComma) {
                message.append(", ");
            }
            message.append("one digit");
            needsComma = true;
        }

        if (!password.matches(".*[^A-Za-z0-9].*")) {
            if (needsComma) {
                message.append(", ");
            }
            message.append("one special character");
        }

        if (message.length() > "Password must contain at least ".length()) {
            val lastCommaIndex = message.lastIndexOf(",");
            if (lastCommaIndex > -1) {
                message.replace(lastCommaIndex, lastCommaIndex + 1, " and");
            }
            throw new UserInvalidException(message.toString());
        }
    }

    public static void validateUserDetailsNotEmpty(UpdateUserRequest request) {
        if (request == null) {
            throw new UserInvalidException(ApiMessages.USER_DETAILS_EMPTY_ERROR.getMessage());
        }

        validateUserDetailsNotEmpty(request.name(), request.address(), request.email(),
                request.countryCode(), request.phoneNumber(), request.password());
    }

    public static void validateUserDetailsNotEmpty(User user) {
        if (user == null) {
            throw new UserInvalidException(ApiMessages.USER_DETAILS_EMPTY_ERROR.getMessage());
        }

        validateUserDetailsNotEmpty(user.getName(), user.getAddress(), user.getEmail(),
                user.getCountryCode(), user.getPhoneNumber(), user.getPassword());
    }

    private static void validateUserDetailsNotEmpty(String name, String address, String email,
            String countryCode, String phoneNumber, String password) {

        if (name == null || name.isEmpty()) {
            throw new UserInvalidException(ApiMessages.USER_NAME_EMPTY_ERROR.getMessage());
        }

        if (address == null || address.isEmpty()) {
            throw new UserInvalidException(ApiMessages.USER_ADDRESS_EMPTY_ERROR.getMessage());
        }

        if (email == null || email.isEmpty()) {
            throw new UserInvalidException(ApiMessages.USER_EMAIL_EMPTY_ERROR.getMessage());
        }

        if (countryCode == null || countryCode.isEmpty()) {
            throw new UserInvalidException(ApiMessages.USER_COUNTRY_CODE_EMPTY_ERROR.getMessage());
        }

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new UserInvalidException(ApiMessages.USER_PHONE_NUMBER_EMPTY_ERROR.getMessage());
        }

        if (password == null || password.isEmpty()) {
            throw new UserInvalidException(ApiMessages.PASSWORD_EMPTY_ERROR.getMessage());
        }
    }

    public static void validateUserDetails(UpdateUserRequest request) {
        validateUserDetailsNotEmpty(request);
        validateUserDetails(request.email(), request.countryCode(), request.phoneNumber(), request.password());
    }

    public static void validateUserDetails(User user) {
        validateUserDetailsNotEmpty(user);
        validateUserDetails(user.getEmail(), user.getCountryCode(), user.getPhoneNumber(), user.getPassword());
    }

    private static void validateUserDetails(String email, String countryCode, String phoneNumber, String password) {
        if (!isValidEmail(email)) {
            throw new UserInvalidException(String.format(ApiMessages.USER_EMAIL_ADDRESS_INVALID_ERROR.getMessage(), email));
        }

        if (!isValidCountryCode(countryCode)) {
            throw new UserInvalidException(String.format(ApiMessages.USER_COUNTRY_CODE_INVALID_ERROR.getMessage(), countryCode));
        }

        if (!isValidPhoneNumber(phoneNumber, countryCode)) {
            throw new UserInvalidException(String.format(ApiMessages.USER_PHONE_NUMBER_INVALID_ERROR.getMessage(), phoneNumber, countryCode));
        }

        validatePassword(password);
    }

    public void validateNewUser(User user) {
        validateUserDetails(user);
        if (doesEmailExist(user.getEmail())) {
            throw new UserInvalidException(ApiMessages.USER_EMAIL_ALREADY_EXISTS_ERROR.getMessage());
        }
        if (doesPhoneNumberExist(user.getPhoneNumber())) {
            throw new UserInvalidException(ApiMessages.USER_PHONE_NUMBER_ALREADY_EXISTS_ERROR.getMessage());
        }
    }

    public boolean doesAccountExist(String accountNumber) {
        return userRepository.findByAccountAccountNumber(accountNumber).isPresent();
    }

    public boolean doesEmailExist(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public boolean doesPhoneNumberExist(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber).isPresent();
    }

}
