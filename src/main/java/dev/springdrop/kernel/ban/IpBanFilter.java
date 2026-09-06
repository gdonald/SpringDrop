package dev.springdrop.kernel.ban;

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
 * Refuses a banned address before routing, so a banned visitor reaches no
 * controller and no session of their own. It runs after the trusted-host check
 * and ahead of everything else.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IpBanFilter extends OncePerRequestFilter {

    public static final String MESSAGE = "This address has been banned from this site.";

    private final IpBanService bans;

    public IpBanFilter(IpBanService bans) {
        this.bans = bans;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (bans.isBanned(request.getRemoteAddr())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
