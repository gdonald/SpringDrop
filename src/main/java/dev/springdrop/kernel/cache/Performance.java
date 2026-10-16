package dev.springdrop.kernel.cache;

import dev.springdrop.kernel.asset.AssetProperties;
import dev.springdrop.kernel.config.ConfigStore;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * The performance settings as the site applies them, and clearing every cache
 * the site keeps.
 */
@Component
public class Performance {

    private final ConfigStore config;
    private final CacheManager caches;
    private final PerformanceSettings defaults;

    public Performance(ConfigStore config, CacheManager caches, AssetProperties assets,
            @Value("${springdrop.cache.page-max-age:PT10M}") Duration pageMaxAge) {
        this.config = config;
        this.caches = caches;
        this.defaults = new PerformanceSettings(pageMaxAge.toSeconds(), assets.aggregate());
    }

    public PerformanceSettings settings() {
        return config.read(PerformanceSettings.CONFIG_NAME, PerformanceSettings.class, defaults);
    }

    public void save(PerformanceSettings settings) {
        config.save(PerformanceSettings.CONFIG_NAME, settings);
    }

    /** Empties every cache, so each page, render, and listing is built again when next asked for. */
    public void clearCaches() {
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
    }
}
