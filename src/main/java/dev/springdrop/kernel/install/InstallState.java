package dev.springdrop.kernel.install;

import dev.springdrop.kernel.state.StateService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Whether the installer has run: the time it finished, kept in the state store. */
@Component
public class InstallState {

    static final String COLLECTION = "system";

    static final String KEY = "install_time";

    private final StateService state;
    private final InstallProperties properties;
    private final Clock clock;

    public InstallState(StateService state, InstallProperties properties, Clock clock) {
        this.state = state;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean installed() {
        return properties.assumeInstalled() || state.get(COLLECTION, KEY, Instant.class).isPresent();
    }

    public void markInstalled() {
        state.set(COLLECTION, KEY, clock.instant());
    }

    /** Forgets that the installer ran, so the next request starts it again. */
    public void forget() {
        state.remove(COLLECTION, KEY);
    }
}
