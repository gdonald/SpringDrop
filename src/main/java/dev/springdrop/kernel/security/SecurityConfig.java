package dev.springdrop.kernel.security;

import dev.springdrop.kernel.routing.RouteRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;

/**
 * The kernel security chain. Public routes (front page, error pages, health,
 * static assets) are open; every other request is authorized against the route
 * registry. CSRF protection is on for state-changing requests. There is no login
 * UI yet, so an unauthorized request yields a themed 403 rather than a redirect;
 * authentication arrives with the user system.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RouteRegistry routeRegistry) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/error", "/actuator/health", "/actuator/info",
                                "/js/**", "/css/**", "/webjars/**", "/favicon.ico").permitAll()
                        .anyRequest().access(new RouteAuthorizationManager(routeRegistry)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new Http403ForbiddenEntryPoint()));
        return http.build();
    }
}
