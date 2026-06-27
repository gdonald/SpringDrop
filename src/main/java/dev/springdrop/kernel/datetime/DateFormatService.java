package dev.springdrop.kernel.datetime;

import dev.springdrop.kernel.config.ConfigStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renders timestamps using named date formats. A format is read from config
 * (falling back to the built-in defaults), and the timestamp is rendered in the
 * user's timezone when one is given, otherwise the site default timezone.
 */
@Component
public class DateFormatService {

    private static final DateFormat FALLBACK = new DateFormat("Fallback", "yyyy-MM-dd HH:mm");

    private static final Map<String, DateFormat> DEFAULTS = Map.of(
            "long", new DateFormat("Default long date", "EEEE, MMMM d, yyyy - HH:mm"),
            "medium", new DateFormat("Default medium date", "EEE, MMM d, yyyy - HH:mm"),
            "short", new DateFormat("Default short date", "MM/dd/yyyy - HH:mm"),
            "html_date", new DateFormat("HTML date", "yyyy-MM-dd"),
            "html_time", new DateFormat("HTML time", "HH:mm:ss"));

    private final ConfigStore configStore;

    public DateFormatService(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public String format(Instant instant, String formatId, ZoneId userZone) {
        DateFormat format = configStore.read(
                "date_format." + formatId, DateFormat.class, DEFAULTS.getOrDefault(formatId, FALLBACK));
        ZoneId zone = (userZone != null) ? userZone : siteDefaultZone();
        return DateTimeFormatter.ofPattern(format.pattern(), Locale.ENGLISH).withZone(zone).format(instant);
    }

    private ZoneId siteDefaultZone() {
        SiteDateSettings settings = configStore.read(
                "system.date", SiteDateSettings.class, new SiteDateSettings("UTC"));
        return ZoneId.of(settings.defaultTimezone());
    }
}
