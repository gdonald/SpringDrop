package dev.springdrop.kernel.mail;

import dev.springdrop.kernel.token.TokenContext;
import java.util.Locale;

/**
 * A mail to render and send. The subject and body are message keys resolved for
 * the recipient's language, falling back to the given text itself, and both are
 * run through token replacement. The format names the {@link MailFormatter} that
 * renders the body.
 */
public record MailRequest(
        String to,
        String subject,
        String body,
        TokenContext context,
        Locale locale,
        String format) {

    public static MailRequest plainText(String to, String subject, String body, TokenContext context) {
        return new MailRequest(to, subject, body, context, Locale.ENGLISH, PlainTextMailFormatter.ID);
    }
}
