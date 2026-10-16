package dev.springdrop.kernel.datetime;

import java.time.DayOfWeek;

/**
 * Site-wide regional settings, stored as the config object {@code system.date}:
 * the timezone used when a request has no user timezone of its own, the
 * site's country as an ISO 3166 code, blank for none, and the day a week
 * starts on, which date widgets carry for a calendar to lay out its weeks.
 */
public record SiteDateSettings(String defaultTimezone, String country, DayOfWeek firstDayOfWeek) {

    public static final String CONFIG_NAME = "system.date";

    public static final SiteDateSettings DEFAULTS = new SiteDateSettings("UTC");

    public SiteDateSettings {
        country = country == null ? "" : country;
        firstDayOfWeek = firstDayOfWeek == null ? DayOfWeek.SUNDAY : firstDayOfWeek;
    }

    public SiteDateSettings(String defaultTimezone) {
        this(defaultTimezone, "", DayOfWeek.SUNDAY);
    }

    public SiteDateSettings withDefaultTimezone(String timezone) {
        return new SiteDateSettings(timezone, country, firstDayOfWeek);
    }
}
