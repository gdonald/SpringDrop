package dev.springdrop.kernel.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@RecordApplicationEvents
class ConfigListTagIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConfigStore config;

    @Autowired
    private ApplicationEvents events;

    @AfterEach
    void removeTheObjects() {
        config.delete("language.entity.xx");
        config.delete("standalone");
    }

    private Set<String> lastInvalidated() {
        return events.stream(CacheTagsInvalidatedEvent.class).reduce((first, second) -> second).orElseThrow().tags();
    }

    @Test
    void savingAnObjectInvalidatesItsTagAndTheListOfItsPrefix() {
        config.save("language.entity.xx", Map.of("label", "Test"));

        assertThat(lastInvalidated()).containsExactlyInAnyOrder("config:language.entity.xx",
                "config_list:language.entity");
    }

    @Test
    void anObjectWithoutAPrefixInvalidatesOnlyItsOwnTag() {
        config.save("standalone", Map.of("on", true));

        assertThat(lastInvalidated()).containsExactly("config:standalone");
    }
}
