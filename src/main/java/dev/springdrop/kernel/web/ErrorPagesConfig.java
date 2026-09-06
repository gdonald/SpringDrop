package dev.springdrop.kernel.web;

/**
 * Site-specific error page paths. Each path is an internal path whose content is
 * served in place of the themed default when a request is denied or unmatched;
 * a blank path leaves the default page in use.
 */
public record ErrorPagesConfig(String forbiddenPath, String notFoundPath) {

    public static final String CONFIG_NAME = "system.error_pages";

    public static final ErrorPagesConfig DEFAULTS = new ErrorPagesConfig("", "");
}
