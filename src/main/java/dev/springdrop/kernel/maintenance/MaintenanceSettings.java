package dev.springdrop.kernel.maintenance;

/**
 * Maintenance mode as the site's administrators set it, stored as the config
 * object {@code system.maintenance}: whether the site is in maintenance mode,
 * and the message its visitors are shown meanwhile.
 */
public record MaintenanceSettings(boolean enabled, String message) {

    public static final String CONFIG_NAME = "system.maintenance";
}
