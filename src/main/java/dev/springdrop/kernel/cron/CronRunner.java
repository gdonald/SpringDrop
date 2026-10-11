package dev.springdrop.kernel.cron;

import dev.springdrop.kernel.lock.LockService;
import dev.springdrop.kernel.state.StateService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs every cron job the site has, in id order, under a lock so two triggers
 * never run the jobs at once. A job that fails is logged and the rest still
 * run. Each finished run is recorded as the last run.
 */
@Component
public class CronRunner {

    public static final String LOCK_NAME = "cron";

    /** How long a run may hold the lock before another trigger may take it, should the run die. */
    public static final Duration LOCK_LEASE = Duration.ofMinutes(15);

    static final String STATE_COLLECTION = "system";

    static final String LAST_RUN_KEY = "cron_last";

    static final String KEY_KEY = "cron_key";

    private static final int KEY_BYTES = 32;

    private static final Logger LOG = LoggerFactory.getLogger(CronRunner.class);

    private final List<CronJob> jobs;
    private final LockService locks;
    private final StateService state;
    private final Clock clock;

    public CronRunner(List<CronJob> jobs, LockService locks, StateService state, Clock clock) {
        this.jobs = jobs.stream().sorted(Comparator.comparing(CronJob::id)).toList();
        this.locks = locks;
        this.state = state;
        this.clock = clock;
    }

    public List<String> jobIds() {
        return jobs.stream().map(CronJob::id).toList();
    }

    /** Runs every job, returning false without running any when another run holds the lock. */
    public boolean run() {
        Optional<String> owner = locks.acquire(LOCK_NAME, LOCK_LEASE);
        if (owner.isEmpty()) {
            return false;
        }
        try {
            jobs.forEach(CronRunner::runJob);
            state.set(STATE_COLLECTION, LAST_RUN_KEY, clock.instant().getEpochSecond());
        } finally {
            locks.release(LOCK_NAME, owner.get());
        }
        return true;
    }

    public Optional<Instant> lastRun() {
        return state.get(STATE_COLLECTION, LAST_RUN_KEY, Long.class).map(Instant::ofEpochSecond);
    }

    /** The secret an outside scheduler puts in the cron URL, made the first time it is asked for. */
    public String key() {
        Optional<String> stored = state.get(STATE_COLLECTION, KEY_KEY, String.class);
        if (stored.isPresent()) {
            return stored.get();
        }
        String key = randomKey();
        state.set(STATE_COLLECTION, KEY_KEY, key);
        return key;
    }

    /** Whether a key given in a cron URL is the site's, compared in constant time. */
    public boolean isKey(String candidate) {
        return MessageDigest.isEqual(key().getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static void runJob(CronJob job) {
        try {
            job.run();
        } catch (RuntimeException failure) {
            LOG.error("Cron job {} failed", job.id(), failure);
        }
    }

    private static String randomKey() {
        byte[] bytes = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
