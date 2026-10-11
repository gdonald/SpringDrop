package dev.springdrop.kernel.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import dev.springdrop.kernel.cron.CronRunner;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class QueueProcessorIntegrationTest extends AbstractIntegrationTest {

    static final String COLLECT = "test.collect";

    static final String FAILS = "test.fails";

    static final String SLOW = "test.slow";

    static final List<String> COLLECTED = new ArrayList<>();

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-04-01T08:00:00Z"));

    @Autowired
    private CronRunner cron;

    @Autowired
    private QueueService siteQueues;

    @Autowired
    private PluginRegistry plugins;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void emptyTheQueues() {
        dsl.deleteFrom(table("queue")).where(field("name").in(COLLECT, FAILS, SLOW)).execute();
        COLLECTED.clear();
    }

    @Test
    void enqueuedItemsAreProcessedByTheirWorkerOnTheNextCronRun() {
        siteQueues.enqueue(COLLECT, "first");
        siteQueues.enqueue(COLLECT, "second");

        cron.run();

        assertThat(COLLECTED).containsExactly("first", "second");
        assertThat(siteQueues.size(COLLECT)).isZero();
    }

    @Test
    void aFailingItemIsRetriedOnALaterRunThenQuarantined() {
        QueueService queues = new QueueService(dsl, objectMapper, CLOCK);
        QueueProcessor processor = new QueueProcessor(plugins, queues, CLOCK);
        queues.enqueue(FAILS, "broken");

        processor.run();
        assertThat(queues.size(FAILS)).isEqualTo(1);
        assertThat(queues.quarantined(FAILS)).isEmpty();

        CLOCK.advance(Duration.ofMinutes(1));
        processor.run();

        assertThat(queues.size(FAILS)).isZero();
        assertThat(queues.quarantined(FAILS)).singleElement().satisfies(item -> {
            assertThat(item.attempts()).isEqualTo(2);
            assertThat(item.lastError()).isEqualTo("java.lang.IllegalStateException: cannot process broken");
        });
    }

    @Test
    void aRunStopsWorkingAQueueOnceTheWorkersTimeBudgetIsSpent() {
        QueueService queues = new QueueService(dsl, objectMapper, CLOCK);
        for (int item = 0; item < 5; item++) {
            queues.enqueue(SLOW, item);
        }

        new QueueProcessor(plugins, queues, CLOCK).run();

        assertThat(queues.size(SLOW)).isEqualTo(3);
    }

    @Test
    void theProcessorIsTheQueueCronJob() {
        assertThat(cron.jobIds()).contains("queue");
    }

    @Test
    void aWorkerRetriesAfterAMinuteAndQuarantinesOnTheThirdFailureUnlessItSaysOtherwise() {
        QueueWorker defaults = new TestWorkers.CollectingWorker();

        assertThat(defaults.maxAttempts()).isEqualTo(3);
        assertThat(defaults.retryDelay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(defaults.timeBudget()).isEqualTo(Duration.ofSeconds(15));
    }

    @TestConfiguration
    static class TestWorkers {

        @SpringDropPlugin(id = COLLECT, type = QueueWorker.class)
        static class CollectingWorker implements QueueWorker {
            @Override
            public void process(QueueItem item) {
                COLLECTED.add(item.data().asString());
            }
        }

        @SpringDropPlugin(id = FAILS, type = QueueWorker.class)
        static class FailingWorker implements QueueWorker {
            @Override
            public void process(QueueItem item) {
                throw new IllegalStateException("cannot process " + item.data().asString());
            }

            @Override
            public int maxAttempts() {
                return 2;
            }
        }

        // Each item takes ten seconds of the fifteen-second budget.
        @SpringDropPlugin(id = SLOW, type = QueueWorker.class)
        static class SlowWorker implements QueueWorker {
            @Override
            public void process(QueueItem item) {
                CLOCK.advance(Duration.ofSeconds(10));
            }
        }
    }
}
