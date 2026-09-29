package com.webapp.bankingportal.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;

import lombok.val;

/**
 * Captures every log event reaching the root logger so tests can assert on
 * both the audit events and the absence of sensitive values anywhere in the
 * application's log output.
 */
final class LogCapture implements AutoCloseable {

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture() {
        appender.start();
        rootLogger.addAppender(appender);
    }

    List<ILoggingEvent> events() {
        return new ArrayList<>(appender.list);
    }

    List<ILoggingEvent> auditEvents() {
        return events().stream()
                .filter(event -> SecurityAuditLogger.LOGGER_NAME.equals(event.getLoggerName()))
                .collect(Collectors.toList());
    }

    ILoggingEvent singleAuditEvent() {
        val auditEvents = auditEvents();
        assertThat(auditEvents).hasSize(1);
        return auditEvents.get(0);
    }

    static Map<String, String> keyValues(ILoggingEvent event) {
        assertThat(event.getKeyValuePairs()).isNotNull();
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(kv -> kv.key, kv -> String.valueOf(kv.value)));
    }

    /** Asserts no captured event exposes any of the given values in any rendered form. */
    void assertNoneContain(String... sensitiveValues) {
        val rendered = events().stream().map(LogCapture::render).collect(Collectors.toList());
        for (String value : sensitiveValues) {
            assertThat(rendered)
                    .as("log output must not contain sensitive value '%s'", value)
                    .noneMatch(line -> line.contains(value));
        }
    }

    private static String render(ILoggingEvent event) {
        val sb = new StringBuilder(event.getFormattedMessage());
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(kv -> sb.append(' ').append(kv.key).append('=').append(kv.value));
        }
        if (event.getArgumentArray() != null) {
            for (Object arg : event.getArgumentArray()) {
                sb.append(' ').append(arg);
            }
        }
        if (event.getMDCPropertyMap() != null) {
            sb.append(' ').append(event.getMDCPropertyMap());
        }
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            sb.append(' ').append(t.getMessage());
        }
        return sb.toString();
    }

    @Override
    public void close() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

}
