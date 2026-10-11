package dev.springdrop.kernel.language;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Settles the request's language once the person signing in is known, which
 * the account preference needs, and before the page cache answers, whose
 * pages vary by language. Everything after sees the language as the request's
 * locale.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class LanguageNegotiationFilter extends OncePerRequestFilter {

    /** The request attribute holding the negotiated language, as a {@link Locale}. */
    public static final String LOCALE_ATTRIBUTE = LanguageNegotiationFilter.class.getName() + ".locale";

    private final LanguageNegotiator negotiator;

    public LanguageNegotiationFilter(LanguageNegotiator negotiator) {
        this.negotiator = negotiator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Locale locale = Locale.forLanguageTag(negotiator.negotiate(request,
                SecurityContextHolder.getContext().getAuthentication()));
        request.setAttribute(LOCALE_ATTRIBUTE, locale);
        Locale previous = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(locale);
        try {
            chain.doFilter(new NegotiatedRequest(request, locale), response);
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    /** The request with the negotiated language as its only locale. */
    static final class NegotiatedRequest extends HttpServletRequestWrapper {

        private final Locale locale;

        NegotiatedRequest(HttpServletRequest request, Locale locale) {
            super(request);
            this.locale = locale;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(List.of(locale));
        }
    }
}
