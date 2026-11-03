package com.webapp.bankingportal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.webapp.bankingportal.service.EmailServiceImpl;

import lombok.val;

public class EmailServiceImplTests {

    private static final String MALICIOUS_NAME = "<b>Your account is locked</b> <a href=\"https://evil.example/login\">Verify</a>";
    private static final String ESCAPED_NAME = "&lt;b&gt;Your account is locked&lt;/b&gt; &lt;a href=&quot;https://evil.example/login&quot;&gt;Verify&lt;/a&gt;";

    private final EmailServiceImpl emailService = new EmailServiceImpl(null);

    @Test
    public void test_login_email_template_escapes_user_input() {
        val html = emailService.getLoginEmailTemplate(MALICIOUS_NAME, "<i>time</i>", "<script>x</script>");

        assertFalse(html.contains("<b>Your account is locked</b>"));
        assertFalse(html.contains("evil.example/login\">"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("Hi, " + ESCAPED_NAME + ","));
        assertTrue(html.contains("&lt;i&gt;time&lt;/i&gt;"));
        assertTrue(html.contains("&lt;script&gt;x&lt;/script&gt;"));
    }

    @Test
    public void test_otp_email_template_escapes_user_input() {
        val html = emailService.getOtpLoginEmailTemplate(MALICIOUS_NAME, "xxx123", "123456");

        assertFalse(html.contains("<b>Your account is locked</b>"));
        assertTrue(html.contains("Hi, " + ESCAPED_NAME + ","));
        assertTrue(html.contains("xxx123"));
        assertTrue(html.contains("123456"));
    }

    @Test
    public void test_bank_statement_email_template_escapes_user_input() {
        val html = emailService.getBankStatementEmailTemplate(MALICIOUS_NAME, "<img src=x onerror=alert(1)>");

        assertFalse(html.contains("<b>Your account is locked</b>"));
        assertFalse(html.contains("<img src=x"));
        assertTrue(html.contains("Dear " + ESCAPED_NAME + ","));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    public void test_email_templates_keep_plain_names_readable() {
        assertTrue(emailService.getLoginEmailTemplate("Jane O'Neil-Smith", "t", "l").contains("Hi, Jane O&#39;Neil-Smith,"));
        assertTrue(emailService.getBankStatementEmailTemplate("John Doe", "Welcome!").contains("Dear John Doe,"));
    }
}
