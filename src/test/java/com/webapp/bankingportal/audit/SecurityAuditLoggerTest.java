package com.webapp.bankingportal.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;

import lombok.val;

class SecurityAuditLoggerTest {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "event", "outcome", "auth_method", "pin_operation", "reason", "user_id");

    private final SecurityAuditLogger auditLogger = new SecurityAuditLogger();
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        logs = new LogCapture();
    }

    @AfterEach
    void tearDown() {
        logs.close();
    }

    @Test
    void loginSuccess_isStructuredInfoEvent() {
        auditLogger.loginSucceeded(AuthMethod.PASSWORD, 42L);

        val event = logs.singleAuditEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .isEqualTo("security_audit event=LOGIN_SUCCESS outcome=SUCCESS auth_method=PASSWORD user_id=42");
        assertThat(LogCapture.keyValues(event))
                .containsEntry("event", "LOGIN_SUCCESS")
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("auth_method", "PASSWORD")
                .containsEntry("user_id", "42");
    }

    @Test
    void loginFailure_isWarnEventAndOmitsUnknownUser() {
        auditLogger.loginFailed(AuthMethod.OTP, AuditFailureReason.UNKNOWN_IDENTIFIER, null);

        val event = logs.singleAuditEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .isEqualTo("security_audit event=LOGIN_FAILURE outcome=FAILURE auth_method=OTP reason=UNKNOWN_IDENTIFIER");
        assertThat(LogCapture.keyValues(event)).doesNotContainKey("user_id");
    }

    @Test
    void pinAndTokenEvents_useOnlyAllowlistedKeys() {
        auditLogger.pinChanged(PinOperation.UPDATE, 7L);
        auditLogger.pinChangeFailed(PinOperation.CREATE, AuditFailureReason.INVALID_NEW_PIN, 7L);
        auditLogger.tokenRevoked(TokenRevocationReason.LOGOUT, 7L);

        val events = logs.auditEvents();
        assertThat(events).extracting(e -> LogCapture.keyValues(e).get("event"))
                .containsExactly("PIN_CHANGE_SUCCESS", "PIN_CHANGE_FAILURE", "TOKEN_REVOKED");
        events.forEach(e -> {
            assertThat(LogCapture.keyValues(e).keySet()).isSubsetOf(ALLOWED_KEYS);
            assertThat(e.getArgumentArray()).isNull();
            assertThat(e.getThrowableProxy()).isNull();
        });
    }

}
