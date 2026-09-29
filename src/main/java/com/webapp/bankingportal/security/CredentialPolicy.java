package com.webapp.bankingportal.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.webapp.bankingportal.entity.User;
import com.webapp.bankingportal.exception.InvalidPinException;
import com.webapp.bankingportal.exception.UserInvalidException;
import com.webapp.bankingportal.util.ApiMessages;
import com.webapp.bankingportal.util.ValidationUtil;

import lombok.val;

/**
 * Central password / PIN strength and rotation policy.
 * See docs/credential-policy.md for the full rule set and rotation flow.
 */
@Component
public class CredentialPolicy {

    private static final String PASSWORD_BLOCKLIST = "/security/common-passwords.txt";
    private static final String PIN_BLOCKLIST = "/security/common-pins.txt";
    private static final int MIN_PERSONAL_TOKEN_LENGTH = 4;

    private static final Map<Character, Character> LEET = Map.of(
            '@', 'a', '4', 'a', '3', 'e', '1', 'i', '!', 'i',
            '0', 'o', '$', 's', '5', 's', '7', 't');

    private static final Set<String> COMMON_PASSWORDS = loadList(PASSWORD_BLOCKLIST);
    private static final Set<String> COMMON_PINS = loadList(PIN_BLOCKLIST);

    private final Duration passwordMaxAge;
    private final Duration pinMaxAge;

    public CredentialPolicy(
            @Value("${banking.credentials.password-max-age-days:90}") long passwordMaxAgeDays,
            @Value("${banking.credentials.pin-max-age-days:180}") long pinMaxAgeDays) {
        this.passwordMaxAge = Duration.ofDays(passwordMaxAgeDays);
        this.pinMaxAge = Duration.ofDays(pinMaxAgeDays);
    }

    public void validatePassword(String password, User owner) {
        if (password == null || password.isEmpty()) {
            throw new UserInvalidException(ApiMessages.PASSWORD_EMPTY_ERROR.getMessage());
        }

        ValidationUtil.validatePassword(password);

        if (isCommonPassword(password)) {
            throw new UserInvalidException(ApiMessages.PASSWORD_BREACHED_ERROR.getMessage());
        }

        if (owner != null && containsPersonalInfo(password, owner)) {
            throw new UserInvalidException(ApiMessages.PASSWORD_CONTAINS_PERSONAL_INFO_ERROR.getMessage());
        }
    }

    public void validatePin(String pin, User owner) {
        if (pin == null || pin.isEmpty()) {
            throw new InvalidPinException(ApiMessages.PIN_EMPTY_ERROR.getMessage());
        }

        if (!pin.matches("[0-9]{4}")) {
            throw new InvalidPinException(ApiMessages.PIN_FORMAT_INVALID_ERROR.getMessage());
        }

        if (isWeakPin(pin)) {
            throw new InvalidPinException(ApiMessages.PIN_TOO_WEAK_ERROR.getMessage());
        }

        if (owner != null && owner.getPhoneNumber() != null) {
            val phoneDigits = owner.getPhoneNumber().replaceAll("[^0-9]", "");
            if (phoneDigits.endsWith(pin)) {
                throw new InvalidPinException(ApiMessages.PIN_MATCHES_PHONE_ERROR.getMessage());
            }
        }
    }

    public boolean isRotationDue(LocalDateTime lastChangedAt, boolean isPin) {
        if (lastChangedAt == null) {
            return true;
        }
        val maxAge = isPin ? pinMaxAge : passwordMaxAge;
        return lastChangedAt.plus(maxAge).isBefore(LocalDateTime.now());
    }

    public static boolean isCommonPassword(String password) {
        val lower = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower)) {
            return true;
        }

        val stripped = stripNonLetterEdges(lower);
        val withoutSuffix = lower.replaceAll("[^a-z]+$", "");

        return Stream.of(
                stripped,
                undoLeet(stripped),
                stripNonLetterEdges(undoLeet(lower)),
                stripNonLetterEdges(undoLeet(withoutSuffix)))
                .anyMatch(COMMON_PASSWORDS::contains);
    }

    public static boolean isWeakPin(String pin) {
        if (COMMON_PINS.contains(pin)) {
            return true;
        }

        val d = pin.chars().map(c -> c - '0').toArray();

        if (Arrays.stream(d).distinct().count() == 1) {
            return true;
        }

        var ascending = true;
        var descending = true;
        for (int i = 1; i < d.length; i++) {
            ascending &= d[i] == d[i - 1] + 1;
            descending &= d[i] == d[i - 1] - 1;
        }
        if (ascending || descending) {
            return true;
        }

        if (d[0] == d[2] && d[1] == d[3]) {
            return true;
        }

        if (d[0] == d[1] && d[2] == d[3]) {
            return true;
        }

        val asNumber = Integer.parseInt(pin);
        return asNumber >= 1940 && asNumber <= 2039;
    }

    private static boolean containsPersonalInfo(String password, User owner) {
        val lower = password.toLowerCase(Locale.ROOT);
        val deLeeted = undoLeet(lower);

        return personalTokens(owner).stream()
                .anyMatch(token -> lower.contains(token) || deLeeted.contains(token));
    }

    private static Set<String> personalTokens(User owner) {
        val raw = new StringBuilder();
        if (owner.getName() != null) {
            raw.append(owner.getName()).append(' ');
        }
        if (owner.getEmail() != null) {
            val email = owner.getEmail();
            val at = email.indexOf('@');
            raw.append(at > 0 ? email.substring(0, at) : email).append(' ');
        }

        return Arrays.stream(raw.toString().toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() >= MIN_PERSONAL_TOKEN_LENGTH)
                .collect(Collectors.toSet());
    }

    private static String undoLeet(String value) {
        val sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            sb.append(LEET.getOrDefault(c, c));
        }
        return sb.toString();
    }

    private static String stripNonLetterEdges(String value) {
        return value.replaceAll("^[^a-z]+", "").replaceAll("[^a-z]+$", "");
    }

    private static Set<String> loadList(String resource) {
        val stream = CredentialPolicy.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing credential blocklist resource: " + resource);
        }

        try (val reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
