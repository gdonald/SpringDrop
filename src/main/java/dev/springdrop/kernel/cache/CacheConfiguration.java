package dev.springdrop.kernel.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The Spring caches the site keeps output in, held in memory. */
@Configuration
public class CacheConfiguration {

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(RenderCache.CACHE_NAME, PageCache.CACHE_NAME);
    }
}
