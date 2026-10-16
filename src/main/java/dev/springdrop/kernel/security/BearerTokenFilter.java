package dev.springdrop.kernel.security;

import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import dev.springdrop.kernel.user.AccountDetailsService;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Signs in a request to a web service endpoint that sends a personal access
 * token as {@code Authorization: Bearer <token>}, as the token's account with
 * that account's permissions, for that request alone. A token that is not
 * valid, or whose account is blocked, is refused with a 401, and an address
 * that sends too many of those is refused with a 429 for a while.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private final ApiTokenService tokens;
    private final AccountDetailsService accounts;
    private final FloodService flood;
    private final ObjectMapper objectMapper;

    public BearerTokenFilter(ApiTokenService tokens, AccountDetailsService accounts, FloodService flood,
            ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.flood = flood;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!ApiResponses.sendsBearerToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String address = request.getRemoteAddr();
        if (!flood.isAllowed(FloodSettings.API_TOKEN_IP, address, FloodSettings.IP_THRESHOLD,
                FloodSettings.IP_WINDOW)) {
            ApiResponses.refuse(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests with bearer tokens that are not valid. Try again later.", objectMapper);
            return;
        }
        Optional<AccountPrincipal> account = ApiResponses.bearerToken(request).flatMap(tokens::accountFor)
                .flatMap(accounts::loadById).filter(AccountPrincipal::isEnabled);
        if (account.isEmpty()) {
            flood.register(FloodSettings.API_TOKEN_IP, address, FloodSettings.IP_WINDOW);
            ApiResponses.refuse(request, response, HttpStatus.UNAUTHORIZED,
                    "The bearer token is not valid, or its account is blocked.", objectMapper);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account.get(), null,
                account.get().getAuthorities()));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }
}
