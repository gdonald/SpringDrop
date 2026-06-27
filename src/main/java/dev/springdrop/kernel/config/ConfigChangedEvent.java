package dev.springdrop.kernel.config;

/**
 * Published when a configuration object is saved, so caches and derived state
 * can react to the change.
 */
public record ConfigChangedEvent(String name) {
}
