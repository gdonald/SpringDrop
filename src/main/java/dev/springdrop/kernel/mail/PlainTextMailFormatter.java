package dev.springdrop.kernel.mail;

import org.springframework.stereotype.Component;

/**
 * Sends the body as written, with no markup around it.
 */
@Component
public class PlainTextMailFormatter implements MailFormatter {

    public static final String ID = "plain";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String contentType() {
        return "text/plain; charset=UTF-8";
    }

    @Override
    public boolean requiresSanitizedTokens() {
        return false;
    }

    @Override
    public String render(String body) {
        return body;
    }
}
