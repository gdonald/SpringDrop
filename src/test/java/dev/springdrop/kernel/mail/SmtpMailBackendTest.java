package dev.springdrop.kernel.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpMailBackendTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private final SmtpMailBackend backend = new SmtpMailBackend(mailSender);

    private MimeMessage newMimeMessage() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new java.util.Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        return mimeMessage;
    }

    private MailMessage message(String replyTo, String contentType) {
        return new MailMessage(
                "user@example.com", "site@example.com", replyTo, "Subject", "Body", contentType);
    }

    @Test
    void sendsTheMessageThroughTheMailSender() throws Exception {
        MimeMessage mimeMessage = newMimeMessage();

        backend.deliver(message("", "text/plain; charset=UTF-8"));

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Subject");
        assertThat(mimeMessage.getRecipients(Message.RecipientType.TO)).hasSize(1);
    }

    @Test
    void setsTheReplyToAddressWhenOneIsConfigured() throws Exception {
        MimeMessage mimeMessage = newMimeMessage();

        backend.deliver(message("replies@example.com", "text/plain; charset=UTF-8"));

        assertThat(mimeMessage.getReplyTo()).hasSize(1);
        assertThat(mimeMessage.getReplyTo()[0].toString()).isEqualTo("replies@example.com");
    }

    @Test
    void sendsAnHtmlBodyAsHtml() throws Exception {
        MimeMessage mimeMessage = newMimeMessage();

        backend.deliver(message("", "text/html; charset=UTF-8"));

        assertThat(mimeMessage.getDataHandler().getContentType()).startsWith("text/html");
    }

    @Test
    void reportsAMessageWithAnUnusableAddress() {
        newMimeMessage();
        MailMessage unaddressable = new MailMessage(
                "@@not an address@@", "site@example.com", "", "Subject", "Body", "text/plain; charset=UTF-8");

        assertThatThrownBy(() -> backend.deliver(unaddressable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@@not an address@@");
    }
}
