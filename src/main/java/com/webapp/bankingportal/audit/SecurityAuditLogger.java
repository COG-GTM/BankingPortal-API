package com.webapp.bankingportal.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

import lombok.val;

/**
 * Emits structured security audit events on the dedicated {@value #LOGGER_NAME}
 * logger.
 *
 * <p>
 * The API only accepts enums and internal numeric user ids, so free-form
 * values such as identifiers, emails, account numbers, passwords, PINs, OTPs
 * or tokens cannot be written to the audit log. Each field is emitted both as
 * an SLF4J key-value pair (for structured encoders) and as a {@code key=value}
 * pair in the message (for plain-text appenders).
 */
@Component
public class SecurityAuditLogger {

    public static final String LOGGER_NAME = "SECURITY_AUDIT";

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger(LOGGER_NAME);

    public void loginSucceeded(AuthMethod method, Long userId) {
        val fields = baseFields(SecurityAuditEvent.LOGIN_SUCCESS, Outcome.SUCCESS);
        fields.put("auth_method", method);
        fields.put("user_id", userId);
        emit(Level.INFO, fields);
    }

    public void loginFailed(AuthMethod method, AuditFailureReason reason, Long userId) {
        val fields = baseFields(SecurityAuditEvent.LOGIN_FAILURE, Outcome.FAILURE);
        fields.put("auth_method", method);
        fields.put("reason", reason);
        fields.put("user_id", userId);
        emit(Level.WARN, fields);
    }

    public void pinChanged(PinOperation operation, Long userId) {
        val fields = baseFields(SecurityAuditEvent.PIN_CHANGE_SUCCESS, Outcome.SUCCESS);
        fields.put("pin_operation", operation);
        fields.put("user_id", userId);
        emit(Level.INFO, fields);
    }

    public void pinChangeFailed(PinOperation operation, AuditFailureReason reason, Long userId) {
        val fields = baseFields(SecurityAuditEvent.PIN_CHANGE_FAILURE, Outcome.FAILURE);
        fields.put("pin_operation", operation);
        fields.put("reason", reason);
        fields.put("user_id", userId);
        emit(Level.WARN, fields);
    }

    public void tokenRevoked(TokenRevocationReason reason, Long userId) {
        val fields = baseFields(SecurityAuditEvent.TOKEN_REVOKED, Outcome.SUCCESS);
        fields.put("reason", reason);
        fields.put("user_id", userId);
        emit(Level.INFO, fields);
    }

    private enum Outcome {
        SUCCESS,
        FAILURE
    }

    private static Map<String, Object> baseFields(SecurityAuditEvent event, Outcome outcome) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event", event);
        fields.put("outcome", outcome);
        return fields;
    }

    private static void emit(Level level, Map<String, Object> fields) {
        fields.values().removeIf(value -> value == null);

        LoggingEventBuilder builder = AUDIT_LOG.atLevel(level);
        fields.forEach(builder::addKeyValue);

        val message = fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" ", "security_audit ", ""));
        builder.log(message);
    }

}
