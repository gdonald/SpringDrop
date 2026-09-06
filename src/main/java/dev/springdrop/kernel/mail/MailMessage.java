package dev.springdrop.kernel.mail;

/**
 * A rendered mail ready for delivery, with the addresses and content type the
 * backend needs to hand it to a transport.
 */
public record MailMessage(
        String to,
        String from,
        String replyTo,
        String subject,
        String body,
        String contentType) {
}
