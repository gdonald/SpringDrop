package dev.springdrop.kernel.token;

import dev.springdrop.kernel.datetime.DateFormatService;
import java.time.Clock;
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
}
