package dev.springdrop.kernel.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@RecordApplicationEvents
class ConfigStoreIntegrationTest extends AbstractIntegrationTest {

    record SiteInfo(String name, String slogan) {
    }

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private ApplicationEvents events;

    private static final SiteInfo DEFAULTS = new SiteInfo("SpringDrop", "");

    @Test
    void returnsDefaultsWhenNothingIsStored() {
        SiteInfo result = configStore.read("system.site.absent", SiteInfo.class, DEFAULTS);
        assertThat(result).isEqualTo(DEFAULTS);
    }

    @Test
    void savedConfigIsReadBack() {
        configStore.save("system.site.saved", new SiteInfo("My Site", "Hello"));

        SiteInfo result = configStore.read("system.site.saved", SiteInfo.class, DEFAULTS);
        assertThat(result).isEqualTo(new SiteInfo("My Site", "Hello"));
    }

    @Test
    void savingPublishesAChangeEvent() {
        configStore.save("system.site.event", new SiteInfo("Eventful", ""));

        assertThat(events.stream(ConfigChangedEvent.class).map(ConfigChangedEvent::name))
                .contains("system.site.event");
    }

    @Test
    void appliesEnvironmentOverridesOnReadWithoutMutatingStorage() {
        configStore.save("system.site.overridden", new SiteInfo("Stored Name", "Stored slogan"));

        SiteInfo result = configStore.read("system.site.overridden", SiteInfo.class, DEFAULTS);
        assertThat(result.name()).isEqualTo("Overridden Name");
        assertThat(result.slogan()).isEqualTo("Stored slogan");
    }

    @TestConfiguration
    static class Overrides {

        @Bean
        ConfigOverrideProvider siteNameOverride() {
            return configName -> "system.site.overridden".equals(configName)
                    ? Optional.of(Map.of("name", "Overridden Name"))
                    : Optional.empty();
        }
    }
}
