package dev.springdrop.kernel.mail;

import dev.springdrop.kernel.token.TokenContext;
import dev.springdrop.kernel.token.TokenReplacer;
import org.springframework.stereotype.Component;

/**
 * Renders a mail's subject and body by running them through token replacement,
 * then hands the result to the configured {@link MailBackend}.
 */
@Component
public class MailManager {

    private final MailBackend backend;
    private final TokenReplacer tokenReplacer;

    public MailManager(MailBackend backend, TokenReplacer tokenReplacer) {
        this.backend = backend;
        this.tokenReplacer = tokenReplacer;
    }

    public void send(String to, String subjectTemplate, String bodyTemplate, TokenContext context) {
        String subject = tokenReplacer.replace(subjectTemplate, context);
        String body = tokenReplacer.replace(bodyTemplate, context);
        backend.deliver(new MailMessage(to, subject, body));
    }
}
