package dev.springdrop.kernel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes the site's security headers and mints the nonce the page's inline
 * scripts carry. The nonce is put on the request before anything renders, so a
 * template reads it as {@code ${cspNonce}} and a script without it is refused by
 * the browser.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String NONCE_ATTRIBUTE = "cspNonce";

    private static final int NONCE_BYTES = 16;

    private final SecurityHeadersProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SecurityHeadersFilter(SecurityHeadersProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String nonce = newNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);

        setIfConfigured(response, "X-Frame-Options", properties.frameOptions());
        setIfConfigured(response, "X-Content-Type-Options", properties.contentTypeOptions());
        setIfConfigured(response, "Referrer-Policy", properties.referrerPolicy());
        setIfConfigured(response, "Content-Security-Policy", properties.policyWith(nonce));

        filterChain.doFilter(request, response);
    }

    private String newNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void setIfConfigured(HttpServletResponse response, String name, String value) {
        if (!value.isBlank()) {
            response.setHeader(name, value);
        }
    }
}
