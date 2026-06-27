package dev.springdrop.kernel.datetime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DateFormatServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOON_UTC = Instant.parse("2026-01-15T12:00:00Z");

    @Autowired
    private DateFormatService dateFormatService;

    @Autowired
    private ConfigStore configStore;

    @Test
    void rendersInTheSiteDefaultTimezoneWhenNoUserZoneIsGiven() {
        assertThat(dateFormatService.format(NOON_UTC, "short", null))
                .isEqualTo("01/15/2026 - 12:00");
    }

    @Test
    void rendersInTheUserTimezoneWhenGiven() {
        assertThat(dateFormatService.format(NOON_UTC, "short", ZoneId.of("America/New_York")))
                .isEqualTo("01/15/2026 - 07:00");
    }

    @Test
    void usesAStoredFormatOverrideWhenPresent() {
        configStore.save("date_format.iso_day", new DateFormat("ISO day", "yyyy/MM/dd"));

        assertThat(dateFormatService.format(NOON_UTC, "iso_day", ZoneId.of("UTC")))
                .isEqualTo("2026/01/15");
    }

    @Test
    void fallsBackForAnUnknownFormatId() {
        assertThat(dateFormatService.format(NOON_UTC, "nonexistent", ZoneId.of("UTC")))
                .isEqualTo("2026-01-15 12:00");
    }
}
