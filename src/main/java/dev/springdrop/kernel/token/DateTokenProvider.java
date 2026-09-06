package dev.springdrop.kernel.token;

import dev.springdrop.kernel.datetime.DateFormatService;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code [date:FORMAT]} tokens to the current time rendered in the named
 * date format, for example {@code [date:short]}.
 */
@Component
public class DateTokenProvider implements TokenProvider {

    private final DateFormatService dateFormatService;
    private final Clock clock;

    public DateTokenProvider(DateFormatService dateFormatService, Clock clock) {
        this.dateFormatService = dateFormatService;
        this.clock = clock;
    }

    @Override
    public String type() {
        return "date";
    }

    @Override
    public String resolve(String name, TokenContext context) {
        return dateFormatService.format(clock.instant(), name, null);
    }

    @Override
    public List<TokenDefinition> availableTokens() {
        return List.of(
                new TokenDefinition("long", "The current time in the long date format"),
                new TokenDefinition("medium", "The current time in the medium date format"),
                new TokenDefinition("short", "The current time in the short date format"),
                new TokenDefinition("html_date", "The current date as yyyy-MM-dd"),
                new TokenDefinition("html_time", "The current time as HH:mm:ss"));
    }
}
