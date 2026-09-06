package dev.springdrop.kernel.security;

import dev.springdrop.kernel.access.RouteAccessChecker;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;

/**
 * The kernel security chain. Public routes (front page, error pages, static
 * assets) are open, the actuator health and info endpoints follow the actuator
 * policy, and every other request is authorized against the route registry.
 * CSRF protection is on for state-changing requests. There is no login UI yet,
 * so an unauthorized request yields a themed 403 rather than a redirect.
 * Authentication arrives with the user system.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String LOGIN_PATH = "/user/login";

    public static final String LOGOUT_PATH = "/user/logout";

    private static final String REMEMBER_ME_KEY = "springdrop";

    private static final int REMEMBER_ME_DAYS = 14;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RouteAccessChecker accessChecker,
            RedirectSafety redirectSafety,
            Environment environment) throws Exception {

        boolean administratorRequired = environment.acceptsProfiles(Profiles.of("prod"));
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class))
                        .access(new ActuatorAuthorizationManager(administratorRequired))
                        .requestMatchers("/", "/error", LOGIN_PATH, LOGOUT_PATH,
                                "/user/register", "/user/password", "/user/reset/**", "/user/verify/**",
                                "/js/**", "/css/**", "/webjars/**", "/favicon.ico").permitAll()
                        .anyRequest().access(new RouteAuthorizationManager(accessChecker)))
                .formLogin(login -> login
                        .loginPage(LOGIN_PATH)
                        .loginProcessingUrl(LOGIN_PATH)
                        .successHandler(new DestinationSuccessHandler(redirectSafety))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_PATH)
                        .logoutSuccessUrl("/")
                        .permitAll())
                .rememberMe(rememberMe -> rememberMe
                        .key(REMEMBER_ME_KEY)
                        .tokenValiditySeconds(REMEMBER_ME_DAYS * 24 * 60 * 60))
                // A new session on sign-in, so a session id handed to someone
                // before they signed in cannot be used afterwards.
                .sessionManagement(sessions -> sessions
                        .sessionFixation(fixation -> fixation.newSession()))
                // The security headers, and the nonce the pages' inline scripts
                // carry, are written by SecurityHeadersFilter instead.
                .headers(headers -> headers.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new Http403ForbiddenEntryPoint()));
        return http.build();
    }
}
