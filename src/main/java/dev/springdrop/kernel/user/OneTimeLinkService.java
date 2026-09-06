package dev.springdrop.kernel.user;

import dev.springdrop.kernel.state.StateService;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The one-time links a site sends by mail: a token that stands for an account
 * for a while, and only once. Using a token spends it, so a link forwarded to
 * someone else has nothing left to give.
 */
@Component
public class OneTimeLinkService {

    public static final String RESET_PASSWORD = "reset_password";

    public static final String VERIFY_MAIL = "verify_mail";

    public static final Duration DEFAULT_LIFETIME = Duration.ofHours(24);

    static final String STATE_COLLECTION = "user.one_time_link";

    private final StateService stateService;

    public OneTimeLinkService(StateService stateService) {
        this.stateService = stateService;
    }

    /** A token standing for this account and this purpose, good until it lapses. */
    public String issue(long accountId, String purpose, Duration lifetime) {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        stateService.setWithExpiry(STATE_COLLECTION, key(encoded), new Link(accountId, purpose), lifetime);
        return encoded;
    }

    public String issue(long accountId, String purpose) {
        return issue(accountId, purpose, DEFAULT_LIFETIME);
    }

    /** The account a token stands for, spending the token in the process. */
    public Optional<Long> consume(String token, String purpose) {
        Optional<Link> link = stateService.getExpiring(STATE_COLLECTION, key(token), Link.class)
                .filter(stored -> stored.purpose().equals(purpose));
        link.ifPresent(stored -> stateService.removeExpiring(STATE_COLLECTION, key(token)));
        return link.map(found -> found.accountId());
    }

    private static String key(String token) {
        return token;
    }

    /** What a token stands for while it lasts. */
    public record Link(long accountId, String purpose) {
    }
}
