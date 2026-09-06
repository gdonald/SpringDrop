package dev.springdrop.kernel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns away a request addressed to a name this site does not answer to. It runs
 * ahead of everything else so an untrusted host never reaches a controller and
 * never ends up in a generated absolute URL.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrustedHostFilter extends OncePerRequestFilter {

    public static final String MESSAGE = "The host name in this request is not one this site answers to.";

    private final TrustedHostProperties properties;

    public TrustedHostFilter(TrustedHostProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!properties.trusts(request.getServerName())) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
