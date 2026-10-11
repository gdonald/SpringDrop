package dev.springdrop.kernel.language;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the language a URL names when the URL method is on. With prefixes, a
 * path starting with a language's prefix is answered as the path without it,
 * so {@code /fr/node/1} is {@code /node/1} in French. With domains, the host
 * asked for names the language. It runs before aliases are resolved and before
 * access is checked, so both see the path without its prefix.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class LanguageUrlFilter extends OncePerRequestFilter {

    private final LanguageManager languages;
    private final LanguageNegotiator negotiator;

    public LanguageUrlFilter(LanguageManager languages, LanguageNegotiator negotiator) {
        this.languages = languages;
        this.negotiator = negotiator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        NegotiationSettings settings = negotiator.settings();
        if (!settings.methods().contains(NegotiationSettings.URL)) {
            chain.doFilter(request, response);
            return;
        }
        if (settings.urlSource().equals(NegotiationSettings.DOMAIN)) {
            languages.all().stream()
                    .filter(language -> !language.domain().isEmpty()
                            && language.domain().equalsIgnoreCase(request.getServerName()))
                    .findFirst()
                    .ifPresent(language -> request.setAttribute(LanguageNegotiator.URL_LANGUAGE, language.id()));
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<LanguageConfig> prefixed = languages.all().stream()
                .filter(language -> !language.prefix().isEmpty())
                .filter(language -> path.equals("/" + language.prefix())
                        || path.startsWith("/" + language.prefix() + "/"))
                .findFirst();
        if (prefixed.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(LanguageNegotiator.URL_LANGUAGE, prefixed.get().id());
        String rest = path.substring(prefixed.get().prefix().length() + 1);
        chain.doFilter(new UnprefixedRequest(request, rest.isEmpty() ? "/" : rest), response);
    }

    /** The request as if it had asked for the path without the language prefix. */
    static final class UnprefixedRequest extends HttpServletRequestWrapper {

        private final String path;

        UnprefixedRequest(HttpServletRequest request, String path) {
            super(request);
            this.path = path;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + path;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer(super.getRequestURL());
            url.setLength(url.length() - super.getRequestURI().length());
            return url.append(getRequestURI());
        }

        @Override
        public String getServletPath() {
            return path;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
