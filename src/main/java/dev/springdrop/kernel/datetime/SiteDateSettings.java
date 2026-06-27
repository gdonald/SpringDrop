package dev.springdrop.kernel.datetime;

/**
 * Site-wide date settings. The default timezone is used when a request has no
 * user timezone of its own.
 */
public record SiteDateSettings(String defaultTimezone) {
}
