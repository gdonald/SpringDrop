package dev.springdrop.kernel.language;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The address of a page in a language, by the first negotiation method that
 * can name one in a link: the language's prefix or domain when the URL method
 * is on, or the {@code language} parameter when the session method is. A
 * language whose prefix or domain is empty is linked without one.
 */
@Component
public class LanguageLinks {

    private final LanguageNegotiator negotiator;

    public LanguageLinks(LanguageNegotiator negotiator) {
        this.negotiator = negotiator;
    }

    /** The page at the path in the language, or empty when no method on can choose a language from a link. */
    public Optional<String> url(LanguageConfig language, String path) {
        NegotiationSettings settings = negotiator.settings();
        if (settings.methods().contains(NegotiationSettings.URL)) {
            if (settings.urlSource().equals(NegotiationSettings.DOMAIN)) {
                return Optional.of(language.domain().isEmpty() ? path : "//" + language.domain() + path);
            }
            return Optional.of(language.prefix().isEmpty() ? path
                    : "/" + language.prefix() + (path.equals("/") ? "" : path));
        }
        if (settings.methods().contains(NegotiationSettings.SESSION)) {
            return Optional.of(path + "?" + LanguageNegotiator.SESSION_PARAMETER + "=" + language.id());
        }
        return Optional.empty();
    }
}
