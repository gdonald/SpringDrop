package dev.springdrop.kernel.cron;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.lock.LockService;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CronRunnerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:30:00Z");

    @Autowired
    private LockService locks;

    @Autowired
    private StateService state;

    @Autowired
    private CronRunner siteCron;

    private final List<String> ran = new ArrayList<>();

    @BeforeEach
    void cronHasNotRun() {
        state.remove(CronRunner.STATE_COLLECTION, CronRunner.LAST_RUN_KEY);
    }

    private CronJob job(String id, Runnable work) {
        return new CronJob() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void run() {
                work.run();
                ran.add(id);
            }
        };
    }

    private CronRunner runner(CronJob... jobs) {
        return new CronRunner(List.of(jobs), locks, state, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runsEveryJobInIdOrderAndRecordsTheRun() {
        CronRunner cron = runner(job("search.index", () -> { }), job("file.temporary", () -> { }));

        assertThat(cron.run()).isTrue();

        assertThat(ran).containsExactly("file.temporary", "search.index");
        assertThat(cron.jobIds()).containsExactly("file.temporary", "search.index");
        assertThat(cron.lastRun()).contains(NOW);
        assertThat(locks.isHeld(CronRunner.LOCK_NAME)).isFalse();
    }

    @Test
    void aFailingJobDoesNotStopTheJobsAfterIt() {
        CronRunner cron = runner(job("a.fails", () -> {
            throw new IllegalStateException("disk full");
        }), job("b.works", () -> { }));

        assertThat(cron.run()).isTrue();

        assertThat(ran).containsExactly("b.works");
        assertThat(cron.lastRun()).contains(NOW);
    }

    @Test
    void aTriggerWhileARunHoldsTheLockIsLockedOut() {
        List<Boolean> overlapping = new ArrayList<>();
        CronRunner[] cron = new CronRunner[1];
        cron[0] = runner(job("reenters", () -> overlapping.add(cron[0].run())));

        assertThat(cron[0].run()).isTrue();

        assertThat(overlapping).containsExactly(false);
        assertThat(ran).containsExactly("reenters");
    }

    @Test
    void aLockedOutTriggerRunsNoJobAndRecordsNoRun() {
        String owner = locks.acquire(CronRunner.LOCK_NAME, CronRunner.LOCK_LEASE).orElseThrow();
        try {
            assertThat(runner(job("never", () -> { })).run()).isFalse();
        } finally {
            locks.release(CronRunner.LOCK_NAME, owner);
        }

        assertThat(ran).isEmpty();
        assertThat(siteCron.lastRun()).isEmpty();
    }

    @Test
    void theKeyIsMadeOnceAndOnlyItMatches() {
        state.remove(CronRunner.STATE_COLLECTION, CronRunner.KEY_KEY);
        String key = siteCron.key();

        assertThat(runner().key()).isEqualTo(key);
        assertThat(siteCron.isKey(key)).isTrue();
        assertThat(siteCron.isKey(key + "x")).isFalse();
        assertThat(key).hasSize(43);
    }
}
