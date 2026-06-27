package dev.springdrop.kernel.config;

import java.util.Map;
import java.util.Optional;

/**
 * Supplies environment-specific overrides for a named config object. Overrides
 * are applied on read and never mutate the stored config, mirroring Drupal's
 * configuration override layer.
 */
public interface ConfigOverrideProvider {

    Optional<Map<String, Object>> overrides(String configName);
}
