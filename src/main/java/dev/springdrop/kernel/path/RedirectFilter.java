package dev.springdrop.kernel.path;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers a request for a redirect's source with the redirect, before aliases
 * and routing. The request's query string is carried over to a destination
 * that has none of its own.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RedirectFilter extends OncePerRequestFilter {

    private final RedirectManager redirects;

    public RedirectFilter(RedirectManager redirects) {
        this.redirects = redirects;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<Redirect> redirect = redirects.find(path);
        if (redirect.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        String destination = redirect.get().destination();
        if (destination.startsWith("/")) {
            destination = request.getContextPath() + destination;
        }
        if (request.getQueryString() != null && !destination.contains("?")) {
            destination = destination + "?" + request.getQueryString();
        }
        response.setStatus(redirect.get().status());
        response.setHeader(HttpHeaders.LOCATION, destination);
    }
}
