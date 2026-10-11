package dev.springdrop.kernel.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class QueueServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String QUEUE = "test.service";

    private static final Duration LEASE = Duration.ofMinutes(10);

    private static final Duration RETRY = Duration.ofMinutes(1);

    @Autowired
    private DSLContext dsl;

    @Autowired
    private ObjectMapper objectMapper;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-04-01T08:00:00Z"));

    private QueueService queues() {
        return new QueueService(dsl, objectMapper, clock);
    }

    @AfterEach
    void emptyTheQueue() {
        dsl.deleteFrom(table("queue")).where(field("name").eq(QUEUE)).execute();
    }

    @Test
    void itemsAreClaimedOldestFirstWithTheirDataAndLeaveWhenDeleted() {
        QueueService queues = queues();
        long first = queues.enqueue(QUEUE, Map.of("nid", 1));
        queues.enqueue(QUEUE, Map.of("nid", 2));

        QueueItem claimed = queues.claim(QUEUE, LEASE).orElseThrow();

        assertThat(claimed.id()).isEqualTo(first);
        assertThat(claimed.queue()).isEqualTo(QUEUE);
        assertThat(claimed.data().get("nid").asInt()).isEqualTo(1);
        assertThat(claimed.attempts()).isZero();
        queues.delete(claimed);
        assertThat(queues.size(QUEUE)).isEqualTo(1);
    }

    @Test
    void aLeasedItemIsNotClaimedAgainUntilTheLeaseRunsOut() {
        QueueService queues = queues();
        queues.enqueue(QUEUE, "reindex");
        QueueItem claimed = queues.claim(QUEUE, LEASE).orElseThrow();

        assertThat(queues.claim(QUEUE, LEASE)).isEmpty();
        assertThat(queues.size(QUEUE)).isEqualTo(1);

        clock.advance(LEASE);
        assertThat(queues.claim(QUEUE, LEASE)).map(QueueItem::id).contains(claimed.id());
    }

    @Test
    void aReleasedItemIsClaimableAtOnce() {
        QueueService queues = queues();
        queues.enqueue(QUEUE, "reindex");

        queues.release(queues.claim(QUEUE, LEASE).orElseThrow());

        assertThat(queues.claim(QUEUE, LEASE)).isPresent();
    }

    @Test
    void aFailedItemWaitsLongerAfterEachFailureAndIsQuarantinedAtTheLimit() {
        QueueService queues = queues();
        queues.enqueue(QUEUE, "send");

        assertThat(queues.fail(queues.claim(QUEUE, LEASE).orElseThrow(), "timeout", 3, RETRY)).isFalse();
        assertThat(queues.claim(QUEUE, LEASE)).isEmpty();
        clock.advance(RETRY);
        QueueItem second = queues.claim(QUEUE, LEASE).orElseThrow();
        assertThat(second.attempts()).isEqualTo(1);
        assertThat(second.lastError()).isEqualTo("timeout");

        assertThat(queues.fail(second, "timeout", 3, RETRY)).isFalse();
        clock.advance(RETRY);
        assertThat(queues.claim(QUEUE, LEASE)).isEmpty();
        clock.advance(RETRY);
        QueueItem third = queues.claim(QUEUE, LEASE).orElseThrow();

        assertThat(queues.fail(third, "refused", 3, RETRY)).isTrue();
        clock.advance(Duration.ofDays(1));
        assertThat(queues.claim(QUEUE, LEASE)).isEmpty();
        assertThat(queues.size(QUEUE)).isZero();
        assertThat(queues.quarantined(QUEUE)).singleElement().satisfies(item -> {
            assertThat(item.attempts()).isEqualTo(3);
            assertThat(item.lastError()).isEqualTo("refused");
            assertThat(item.data().asString()).isEqualTo("send");
        });
    }
}
