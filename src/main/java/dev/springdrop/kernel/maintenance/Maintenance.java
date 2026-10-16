package dev.springdrop.kernel.maintenance;

import dev.springdrop.kernel.config.ConfigStore;
import org.springframework.stereotype.Component;

/**
 * Whether the site is in maintenance mode and what it tells visitors. A site
 * that has not saved its maintenance settings follows
 * {@code springdrop.maintenance}.
 */
@Component
public class Maintenance {

    private final ConfigStore config;
    private final MaintenanceSettings defaults;

    public Maintenance(ConfigStore config, MaintenanceProperties properties) {
        this.config = config;
        this.defaults = new MaintenanceSettings(properties.enabled(), properties.message());
    }

    public MaintenanceSettings settings() {
        return config.read(MaintenanceSettings.CONFIG_NAME, MaintenanceSettings.class, defaults);
    }

    public void save(MaintenanceSettings settings) {
        config.save(MaintenanceSettings.CONFIG_NAME, settings);
    }
}
