package dev.springdrop.kernel.cron;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.state.StateService;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

// February 29 at midnight, so the schedule is registered but does not fire during the run.
@SpringBootTest(properties = CronScheduler.SCHEDULE_PROPERTY + "=" + CronSchedulerIntegrationTest.SCHEDULE)
class CronSchedulerIntegrationTest extends AbstractIntegrationTest {

    static final String SCHEDULE = "0 0 0 29 2 *";

    @Autowired
    private ScheduledTaskHolder scheduled;

    @Autowired
    private CronRunner cron;

    @Autowired
    private StateService state;

    @AfterEach
    void forgetTheLastRun() {
        state.remove(CronRunner.STATE_COLLECTION, CronRunner.LAST_RUN_KEY);
    }

    @Test
    void theConfiguredScheduleRunsCron() {
        List<CronTask> cronTasks = scheduled.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .toList();

        assertThat(cronTasks).extracting(CronTask::getExpression).containsExactly(SCHEDULE);

        cronTasks.getFirst().getRunnable().run();

        assertThat(cron.lastRun()).isPresent();
    }
}
