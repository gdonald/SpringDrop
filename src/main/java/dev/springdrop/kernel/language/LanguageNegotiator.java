package dev.springdrop.kernel.language;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Chooses the language of a request by trying the negotiation methods in the
 * order the settings give, the first one naming a language the site has
 * winning, and the site's default language when none does.
 */
@Component
public class LanguageNegotiator {

    /** The request attribute holding the language the URL's prefix or domain names. */
    public static final String URL_LANGUAGE = LanguageNegotiator.class.getName() + ".url";

    /** The query parameter choosing a language for the session. */
    public static final String SESSION_PARAMETER = "language";

    /** The session attribute keeping the language the parameter chose. */
    public static final String SESSION_ATTRIBUTE = LanguageNegotiator.class.getName() + ".session";

    private final LanguageManager languages;
    private final UserAccountService accounts;
    private final ConfigStore config;

    public LanguageNegotiator(LanguageManager languages, UserAccountService accounts, ConfigStore config) {
        this.languages = languages;
        this.accounts = accounts;
        this.config = config;
    }

    public NegotiationSettings settings() {
        return config.read(NegotiationSettings.CONFIG_NAME, NegotiationSettings.class, NegotiationSettings.DEFAULTS);
    }

    public void save(NegotiationSettings settings) {
        config.save(NegotiationSettings.CONFIG_NAME, settings);
    }

    public String negotiate(HttpServletRequest request, Authentication who) {
        for (String method : settings().methods()) {
            Optional<String> chosen = switch (method) {
                case NegotiationSettings.URL -> Optional.ofNullable((String) request.getAttribute(URL_LANGUAGE));
                case NegotiationSettings.USER -> preferred(who);
                case NegotiationSettings.SESSION -> session(request);
                default -> Optional.empty();
            };
            Optional<String> known = chosen.filter(langcode -> languages.find(langcode).isPresent());
            if (known.isPresent()) {
                return known.get();
            }
        }
        return languages.defaultLangcode();
    }

    private Optional<String> preferred(Authentication who) {
        return (who != null && who.getPrincipal() instanceof AccountPrincipal account)
                ? accounts.find(account.id()).map(UserAccount::preferredLanguage) : Optional.empty();
    }

    /**
     * The language the parameter names, which is kept in the session for the
     * requests after it when it is one the site has, or the one kept before.
     */
    private Optional<String> session(HttpServletRequest request) {
        String asked = request.getParameter(SESSION_PARAMETER);
        if (asked != null && languages.find(asked).isPresent()) {
            request.getSession().setAttribute(SESSION_ATTRIBUTE, asked);
            return Optional.of(asked);
        }
        HttpSession session = request.getSession(false);
        return session == null ? Optional.empty()
                : Optional.ofNullable((String) session.getAttribute(SESSION_ATTRIBUTE));
    }
}
