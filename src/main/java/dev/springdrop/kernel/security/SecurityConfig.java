package dev.springdrop.kernel.security;

import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountDetailsService;
import dev.springdrop.kernel.user.ApiTokenService;
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
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * The kernel security chain. Public routes (front page, error pages, static
 * assets) are open, the actuator health and info endpoints follow the actuator
 * policy, and every other request is authorized against the route registry.
 * CSRF protection is on for state-changing requests, except a web service
 * request signed in with a bearer token. There is no login UI yet,
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
            RoleManager roles,
            Environment environment,
            ApiTokenService tokens,
            AccountDetailsService accounts,
            FloodService flood,
            ObjectMapper objectMapper) throws Exception {

        boolean administratorRequired = environment.acceptsProfiles(Profiles.of("prod"));
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class))
                        .access(new ActuatorAuthorizationManager(administratorRequired))
                        .requestMatchers("/", "/error", LOGIN_PATH, LOGOUT_PATH,
                                "/user/register", "/user/password", "/user/reset/**", "/user/verify/**",
                                "/js/**", "/assets/**", "/css/**", "/webjars/**", "/favicon.ico").permitAll()
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
                // A web service request signed in with a bearer token carries no
                // cookie a third-party page could ride on, so it needs no CSRF token.
                .csrf(csrf -> csrf.ignoringRequestMatchers(ApiResponses::sendsBearerToken))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new Http403ForbiddenEntryPoint())
                        .defaultAccessDeniedHandlerFor(new ApiAccessDeniedHandler(objectMapper),
                                ApiResponses::isApiPath))
                .addFilterBefore(new BearerTokenFilter(tokens, accounts, flood, objectMapper),
                        AnonymousAuthenticationFilter.class)
                .addFilterAfter(new AnonymousPermissionsFilter(roles), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
