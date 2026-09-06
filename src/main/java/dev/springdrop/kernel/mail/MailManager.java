package dev.springdrop.kernel.mail;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.token.TokenContext;
import dev.springdrop.kernel.token.TokenReplacer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Renders a mail and hands it to the configured {@link MailBackend}. Subject and
 * body are looked up for the recipient's language, token-replaced, and rendered
 * by the requested {@link MailFormatter}. The sender is the site mail address,
 * with the configured reply-to when one is set.
 */
@Component
public class MailManager {

    private final MailBackend backend;
    private final TokenReplacer tokenReplacer;
    private final ConfigStore configStore;
    private final MessageSource messageSource;
    private final Map<String, MailFormatter> formatters;

    public MailManager(
            MailBackend backend,
            TokenReplacer tokenReplacer,
            ConfigStore configStore,
            MessageSource messageSource,
            List<MailFormatter> formatters) {
        this.backend = backend;
        this.tokenReplacer = tokenReplacer;
        this.configStore = configStore;
        this.messageSource = messageSource;
        this.formatters = formatters.stream()
                .collect(Collectors.toMap(formatter -> formatter.id(), Function.identity()));
    }

    public void send(String to, String subjectTemplate, String bodyTemplate, TokenContext context) {
        send(MailRequest.plainText(to, subjectTemplate, bodyTemplate, context));
    }

    public void send(MailRequest request) {
        MailFormatter formatter = formatters.get(request.format());
        if (formatter == null) {
            throw new IllegalArgumentException("No mail formatter registered for format '" + request.format() + "'");
        }

        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
        MailSettings settings = configStore.read(
                MailSettings.CONFIG_NAME, MailSettings.class, MailSettings.DEFAULTS);

        String subject = render(request.subject(), request, false);
        String body = formatter.render(render(request.body(), request, formatter.requiresSanitizedTokens()));

        backend.deliver(new MailMessage(
                request.to(),
                site.mail(),
                settings.replyTo(),
                subject,
                body,
                formatter.contentType()));
    }

    private String render(String template, MailRequest request, boolean sanitize) {
        String localized = messageSource.getMessage(template, null, template, request.locale());
        TokenContext context = request.context();
        return tokenReplacer.replace(
                localized, new TokenContext(context.data(), sanitize, context.clearUnknown()));
    }
}
