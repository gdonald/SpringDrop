package dev.springdrop.kernel.mail;

import dev.springdrop.kernel.token.TokenContext;
import java.util.Locale;
import java.util.Map;

/**
 * A mail to render and send. The subject and body are message keys resolved for
 * the recipient's language, falling back to the given text itself, and both are
 * run through token replacement, unless the request is literal: then they are
 * sent as written, which is how text someone typed into a form travels. The
 * format names the {@link MailFormatter} that renders the body. A reply-to left
 * null is the site's configured one.
 */
public record MailRequest(
        String to,
        String subject,
        String body,
        TokenContext context,
        Locale locale,
        String format,
        String replyTo,
        boolean literal) {

    public MailRequest(String to, String subject, String body, TokenContext context, Locale locale, String format) {
        this(to, subject, body, context, locale, format, null, false);
    }

    public static MailRequest plainText(String to, String subject, String body, TokenContext context) {
        return new MailRequest(to, subject, body, context, Locale.ENGLISH, PlainTextMailFormatter.ID);
    }

    /** A plain text mail sent as written, with the reply-to given, or the site's when null. */
    public static MailRequest literal(String to, String subject, String body, String replyTo) {
        return new MailRequest(to, subject, body, TokenContext.of(Map.of()), Locale.ENGLISH,
                PlainTextMailFormatter.ID, replyTo, true);
    }
}
