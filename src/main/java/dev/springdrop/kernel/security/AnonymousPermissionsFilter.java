package dev.springdrop.kernel.security;

import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives someone who has not signed in the permissions of the anonymous role,
 * read on every request so a change to the role applies at once. It sits in the
 * security chain right after the anonymous visitor is identified, ahead of the
 * route and entity checks that read those permissions.
 */
public class AnonymousPermissionsFilter extends OncePerRequestFilter {

    static final String KEY = "springdrop-anonymous";

    private final RoleManager roles;

    public AnonymousPermissionsFilter(RoleManager roles) {
        this.roles = roles;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken anonymous) {
            List<GrantedAuthority> authorities = new ArrayList<>(anonymous.getAuthorities());
            roles.permissionsOf(List.of(RoleConfig.ANONYMOUS))
                    .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            SecurityContextHolder.getContext().setAuthentication(
                    new AnonymousAuthenticationToken(KEY, anonymous.getPrincipal(), authorities));
        }
        filterChain.doFilter(request, response);
    }
}
