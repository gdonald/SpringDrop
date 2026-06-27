package dev.springdrop.kernel.token;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DateTokenProviderTest extends AbstractIntegrationTest {

    @Autowired
    private DateFormatService dateFormatService;

    @Test
    void resolvesTheCurrentDateInTheNamedFormat() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
        DateTokenProvider provider = new DateTokenProvider(dateFormatService, fixed);

        assertThat(provider.type()).isEqualTo("date");
        assertThat(provider.resolve("short", TokenContext.of(Map.of()))).isEqualTo("01/15/2026 - 12:00");
    }
}
