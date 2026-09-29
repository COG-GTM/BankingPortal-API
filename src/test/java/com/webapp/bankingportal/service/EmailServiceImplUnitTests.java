package com.webapp.bankingportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplUnitTests {

    private static final String TO = "jane@example.com";

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender);
    }

    private MimeMessage givenMimeMessage() {
        var message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);
        return message;
    }

    @Test
    void sendEmail_sendsHtmlMessageAndCompletesFuture() throws Exception {
        var message = givenMimeMessage();

        var future = emailService.sendEmail(TO, "Subject", "<p>Hello</p>");

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        verify(mailSender).send(message);
        assertEquals("Subject", message.getSubject());
        assertEquals(TO, message.getRecipients(Message.RecipientType.TO)[0].toString());
    }

    @Test
    void sendEmail_completesExceptionallyWhenSendFails() {
        var message = givenMimeMessage();
        var failure = new MailSendException("smtp down");
        doThrow(failure).when(mailSender).send(message);

        var future = emailService.sendEmail(TO, "Subject", "<p>Hello</p>");

        assertTrue(future.isCompletedExceptionally());
        var ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(MailSendException.class, ex.getCause());
    }

    @Test
    void sendEmailWithAttachment_attachesFile(@TempDir Path tempDir) throws Exception {
        var message = givenMimeMessage();
        var attachment = Files.writeString(tempDir.resolve("statement.txt"), "statement");

        emailService.sendEmailWithAttachment(TO, "Statement", "<p>See attached</p>", attachment.toString());

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        var content = (MimeMultipart) captor.getValue().getContent();
        assertEquals(2, content.getCount());
        assertEquals("statement.txt", content.getBodyPart(1).getFileName());
    }

    @Test
    void sendEmailWithAttachment_doesNotSendWhenRecipientInvalid() {
        givenMimeMessage();

        emailService.sendEmailWithAttachment("not an address", "Statement", "text", "missing.txt");

        verify(mailSender, org.mockito.Mockito.never()).send(any(MimeMessage.class));
    }

    @Test
    void templates_includeDynamicValues() {
        var login = emailService.getLoginEmailTemplate("Jane", "2024-01-01 10:00", "Paris, France");
        assertTrue(login.contains("Hi, Jane,"));
        assertTrue(login.contains("2024-01-01 10:00"));
        assertTrue(login.contains("Paris, France"));

        var otp = emailService.getOtpLoginEmailTemplate("Jane", "xxx123", "111222");
        assertTrue(otp.contains("xxx123"));
        assertTrue(otp.contains(">111222</h2>"));
        assertTrue(otp.contains(OtpServiceImpl.OTP_EXPIRY_MINUTES + " minutes"));

        var statement = emailService.getBankStatementEmailTemplate("Jane", "line1\nline2");
        assertTrue(statement.contains("Dear Jane,"));
        assertTrue(statement.contains("line1\nline2"));
    }
}
