package dev.springdrop.kernel.state;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StateServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StateService stateService;

    @Test
    void storesAndReadsBackAValue() {
        stateService.set("system", "last_cron", 1234L);

        assertThat(stateService.get("system", "last_cron", Long.class)).contains(1234L);
    }

    @Test
    void returnsEmptyForAnUnknownKey() {
        assertThat(stateService.get("system", "missing", Long.class)).isEmpty();
    }

    @Test
    void overwritesAnExistingValue() {
        stateService.set("system", "counter", 1L);
        stateService.set("system", "counter", 2L);

        assertThat(stateService.get("system", "counter", Long.class)).contains(2L);
    }

    @Test
    void removesAValue() {
        stateService.set("system", "temp", "x");
        stateService.remove("system", "temp");

        assertThat(stateService.get("system", "temp", String.class)).isEmpty();
    }

    @Test
    void readsAnExpiringValueThatHasNotLapsed() {
        stateService.setWithExpiry("cache", "warm", "value", Duration.ofMinutes(5));

        assertThat(stateService.getExpiring("cache", "warm", String.class)).contains("value");
    }

    @Test
    void doesNotReturnAnExpiredValue() {
        stateService.setWithExpiry("cache", "stale", "value", Duration.ofMinutes(-5));

        assertThat(stateService.getExpiring("cache", "stale", String.class)).isEmpty();
    }

    @Test
    void returnsEmptyForAnUnknownExpiringKey() {
        assertThat(stateService.getExpiring("cache", "never-set", String.class)).isEmpty();
    }
}
