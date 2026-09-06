package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.field.types.DateTimeFieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.springframework.web.util.HtmlUtils;

/**
 * A stored moment shown in one of the site's named date formats, in the reader's
 * timezone. With {@code time_ago} turned on it reads as how long ago it was
 * instead, which suits a "last changed" line better than a full date.
 */
@SpringDropPlugin(id = DateTimeFormatter.ID, type = FieldFormatter.class)
public class DateTimeFormatter implements FieldFormatter {

    public static final String ID = "datetime_default";

    public static final String DATE_FORMAT = "date_format";

    public static final String TIMEZONE = "timezone";

    public static final String TIME_AGO = "time_ago";

    public static final String DEFAULT_FORMAT = "medium";

    private final DateFormatService dateFormatService;
    private final Clock clock;

    public DateTimeFormatter(DateFormatService dateFormatService, Clock clock) {
        this.dateFormatService = dateFormatService;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return HtmlUtils.htmlEscape(rendered(context, value));
    }

    String rendered(FormatterContext context, Object value) {
        Instant moment = instantOf(context, value, zoneOf(context));
        if (moment == null) {
            return String.valueOf(value);
        }
        if (Boolean.TRUE.equals(context.setting(TIME_AGO, false))) {
            return RelativeTime.of(moment, clock.instant());
        }
        return dateFormatService.format(moment, context.text(DATE_FORMAT, DEFAULT_FORMAT), zoneOf(context));
    }

    /** The timezone the reader sees, from the display or the site's own. */
    ZoneId zoneOf(FormatterContext context) {
        Object named = context.settings().get(TIMEZONE);
        return (named == null) ? dateFormatService.siteTimezone() : ZoneId.of(named.toString());
    }

    /** The moment a stored value names, or nothing when it is not one. */
    static Instant instantOf(FormatterContext context, Object value, ZoneId zone) {
        String stored = String.valueOf(value);
        try {
            if (DateTimeConstraint.DATE_ONLY.equals(DateTimeFieldType.datetimeType(context.storage()))) {
                return LocalDate.parse(stored).atStartOfDay(zone).toInstant();
            }
            return java.time.OffsetDateTime.parse(stored).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
