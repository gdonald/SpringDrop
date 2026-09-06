package dev.springdrop.kernel.mail;

import org.springframework.stereotype.Component;

/**
 * Wraps the body in a minimal HTML document. Token values are escaped before
 * they reach the body, so a value carrying markup cannot alter the message.
 */
@Component
public class HtmlMailFormatter implements MailFormatter {

    public static final String ID = "html";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String contentType() {
        return "text/html; charset=UTF-8";
    }

    @Override
    public boolean requiresSanitizedTokens() {
        return true;
    }

    @Override
    public String render(String body) {
        return "<!DOCTYPE html><html><body>" + body + "</body></html>";
    }
}
