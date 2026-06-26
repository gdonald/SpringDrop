package dev.springdrop.kernel.maintenance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Maintenance-mode configuration. When {@code enabled} is true, requests are
 * served a maintenance page unless they are excluded or allowed through by a
 * {@link MaintenanceBypass}.
 */
@ConfigurationProperties("springdrop.maintenance")
public record MaintenanceProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("The site is undergoing maintenance. Please check back soon.")
        String message) {
}
