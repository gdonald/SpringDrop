package dev.springdrop.kernel.mail;

/**
 * Renders a mail body for one output format. A formatter is registered as a bean
 * and selected by the id a caller names on the mail request, so a module can add
 * a format without core knowing about it.
 */
public interface MailFormatter {

    String id();

    String contentType();

    /**
     * Whether values substituted into the body must be escaped for this format.
     */
    boolean requiresSanitizedTokens();

    String render(String body);
}
