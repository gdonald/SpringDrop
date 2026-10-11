package dev.springdrop.kernel.lock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LockServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    private LockService locks;

    @Test
    void aHeldLockIsRefusedToASecondCallerUntilItsOwnerReleasesIt() {
        String owner = locks.acquire("test.held", LEASE).orElseThrow();

        assertThat(locks.acquire("test.held", LEASE)).isEmpty();
        assertThat(locks.isHeld("test.held")).isTrue();

        locks.release("test.held", owner);

        assertThat(locks.isHeld("test.held")).isFalse();
        Optional<String> next = locks.acquire("test.held", LEASE);
        assertThat(next).isPresent();
        locks.release("test.held", next.get());
    }

    @Test
    void releasingWithSomeoneElsesTokenLeavesTheLockHeld() {
        String owner = locks.acquire("test.other", LEASE).orElseThrow();

        locks.release("test.other", "not-the-owner");

        assertThat(locks.isHeld("test.other")).isTrue();
        locks.release("test.other", owner);
    }

    @Test
    void aLockWhoseLeaseRanOutIsTakenByTheNextCaller() {
        String lapsed = locks.acquire("test.lapsed", Duration.ofMinutes(-1)).orElseThrow();

        assertThat(locks.isHeld("test.lapsed")).isFalse();
        String owner = locks.acquire("test.lapsed", LEASE).orElseThrow();
        assertThat(owner).isNotEqualTo(lapsed);

        locks.release("test.lapsed", lapsed);
        assertThat(locks.isHeld("test.lapsed")).isTrue();
        locks.release("test.lapsed", owner);
    }
}
