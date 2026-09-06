package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.web.util.HtmlUtils;

/**
 * A moment stored as seconds since the epoch, shown in one of the site's named
 * date formats, or as how long ago it was.
 */
@SpringDropPlugin(id = TimestampFormatter.ID, type = FieldFormatter.class)
public class TimestampFormatter implements FieldFormatter {

    public static final String ID = "timestamp";

    private final DateFormatService dateFormatService;
    private final Clock clock;

    public TimestampFormatter(DateFormatService dateFormatService, Clock clock) {
        this.dateFormatService = dateFormatService;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        Instant moment = Instant.ofEpochSecond(((Number) value).longValue());
        if (Boolean.TRUE.equals(context.setting(DateTimeFormatter.TIME_AGO, false))) {
            return HtmlUtils.htmlEscape(RelativeTime.of(moment, clock.instant()));
        }

        Object named = context.settings().get(DateTimeFormatter.TIMEZONE);
        ZoneId zone = (named == null) ? dateFormatService.siteTimezone() : ZoneId.of(named.toString());
        return HtmlUtils.htmlEscape(dateFormatService.format(
                moment, context.text(DateTimeFormatter.DATE_FORMAT, DateTimeFormatter.DEFAULT_FORMAT), zone));
    }
}
