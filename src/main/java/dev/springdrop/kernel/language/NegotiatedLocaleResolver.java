package dev.springdrop.kernel.language;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Spring MVC's locale for a request: the language negotiated for it. A request
 * the negotiation filter did not see keeps the locale it asked for. The
 * language is changed through negotiation, never set here.
 */
@Component("localeResolver")
public class NegotiatedLocaleResolver implements LocaleResolver {

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return (request.getAttribute(LanguageNegotiationFilter.LOCALE_ATTRIBUTE) instanceof Locale negotiated)
                ? negotiated : request.getLocale();
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("The language is chosen by negotiation.");
    }
}
