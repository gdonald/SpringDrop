package dev.springdrop.kernel.flood;

import dev.springdrop.kernel.state.StateService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Counts how often something has been tried lately, so a site can turn away a
 * flood of attempts without turning away everyone. Each event is counted per
 * identifier, such as the account being signed in to or the address doing the
 * signing in, and the count fades as its window passes.
 */
@Component
public class FloodService {

    static final String STATE_COLLECTION = "flood";

    private final StateService stateService;
    private final Clock clock;

    public FloodService(StateService stateService, Clock clock) {
        this.stateService = stateService;
        this.clock = clock;
    }

    /** Records one attempt, which counts against the identifier until the window passes. */
    public void register(String event, String identifier, Duration window) {
        List<Long> attempts = new ArrayList<>(recent(event, identifier, window));
        attempts.add(clock.instant().getEpochSecond());
        stateService.setWithExpiry(STATE_COLLECTION, key(event, identifier),
                new Attempts(attempts), window);
    }

    /** Whether another attempt is still within what the site allows. */
    public boolean isAllowed(String event, String identifier, int threshold, Duration window) {
        return recent(event, identifier, window).size() < threshold;
    }

    public int attempts(String event, String identifier, Duration window) {
        return recent(event, identifier, window).size();
    }

    /** Forgets the attempts against one identifier, as a success does. */
    public void clear(String event, String identifier) {
        stateService.removeExpiring(STATE_COLLECTION, key(event, identifier));
    }

    private List<Long> recent(String event, String identifier, Duration window) {
        Instant since = clock.instant().minus(window);
        return stateService.getExpiring(STATE_COLLECTION, key(event, identifier), Attempts.class)
                .map(attempts -> attempts.seconds())
                .orElseGet(List::of).stream()
                .filter(second -> Instant.ofEpochSecond(second).isAfter(since))
                .toList();
    }

    private static String key(String event, String identifier) {
        return event + "." + identifier;
    }

    /** When each attempt happened, as seconds since the epoch. */
    public record Attempts(List<Long> seconds) {

        public Attempts {
            seconds = List.copyOf(seconds);
        }
    }
}
