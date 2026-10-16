package dev.springdrop.kernel.config.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The directory config is exported to and imported from, one YAML file per
 * object. A relative path is read from the directory the application runs in.
 */
@ConfigurationProperties("springdrop.config")
public record ConfigSyncProperties(@DefaultValue("config/sync") String syncDirectory) {
}
