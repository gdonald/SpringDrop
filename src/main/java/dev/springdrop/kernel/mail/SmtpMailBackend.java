package dev.springdrop.kernel.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends mail over SMTP through Spring's {@link JavaMailSender}. It is the
 * primary backend in production; elsewhere the collecting backend keeps mail in
 * memory so nothing leaves the machine.
 */
@Component
@Primary
@Profile("prod")
public class SmtpMailBackend implements MailBackend {

    private static final String HTML_CONTENT_TYPE_PREFIX = "text/html";

    private final JavaMailSender mailSender;

    public SmtpMailBackend(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void deliver(MailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setTo(message.to());
            helper.setFrom(message.from());
            if (!message.replyTo().isBlank()) {
                helper.setReplyTo(message.replyTo());
            }
            helper.setSubject(message.subject());
            helper.setText(message.body(), message.contentType().startsWith(HTML_CONTENT_TYPE_PREFIX));
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to build the mail message for " + message.to(), e);
        }
        mailSender.send(mimeMessage);
    }
}
