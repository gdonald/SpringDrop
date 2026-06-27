package dev.springdrop.kernel.mail;

/**
 * Delivers a rendered mail. The default backend collects messages; an SMTP
 * backend over Spring Mail replaces it when mail is configured for production.
 */
public interface MailBackend {

    void deliver(MailMessage message);
}
