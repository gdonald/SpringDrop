package dev.springdrop.kernel.mail;

/**
 * A rendered mail ready for delivery.
 */
public record MailMessage(String to, String subject, String body) {
}
