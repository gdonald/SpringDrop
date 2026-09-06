package dev.springdrop.kernel.user;

import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Counts failed sign-ins and turns away further attempts once there have been
 * too many, per account and per address. Signing in successfully forgets the
 * attempts against that account, so someone who mistypes and then gets it right
 * is not left locked out.
 */
@Component
public class LoginFloodGuard {

    private final FloodService flood;

    public LoginFloodGuard(FloodService flood) {
        this.flood = flood;
    }

    /** Whether this account and address may try again. */
    public boolean mayAttempt(String username, String address) {
        return flood.isAllowed(FloodSettings.LOGIN_USER, username,
                        FloodSettings.USER_THRESHOLD, FloodSettings.USER_WINDOW)
                && flood.isAllowed(FloodSettings.LOGIN_IP, address,
                        FloodSettings.IP_THRESHOLD, FloodSettings.IP_WINDOW);
    }

    public void recordFailure(String username, String address) {
        flood.register(FloodSettings.LOGIN_USER, username, FloodSettings.USER_WINDOW);
        flood.register(FloodSettings.LOGIN_IP, address, FloodSettings.IP_WINDOW);
    }

    public void recordSuccess(String username) {
        flood.clear(FloodSettings.LOGIN_USER, username);
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        recordFailure(String.valueOf(event.getAuthentication().getName()),
                addressOf(event.getAuthentication().getDetails()));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        recordSuccess(event.getAuthentication().getName());
    }

    /** The address behind an attempt, as the web layer recorded it. */
    private static String addressOf(Object details) {
        if (details instanceof org.springframework.security.web.authentication.WebAuthenticationDetails web) {
            return web.getRemoteAddress();
        }
        return "unknown";
    }
}
