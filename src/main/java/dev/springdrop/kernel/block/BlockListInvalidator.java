package dev.springdrop.kernel.block;

import dev.springdrop.kernel.cache.CacheTagInvalidator;
import dev.springdrop.kernel.config.ConfigChangedEvent;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Invalidates {@link BlockPlacement#LIST_CACHE_TAG} when any placement is saved or deleted. */
@Component
public class BlockListInvalidator {

    private final CacheTagInvalidator invalidator;

    public BlockListInvalidator(CacheTagInvalidator invalidator) {
        this.invalidator = invalidator;
    }

    @EventListener
    void placementChanged(ConfigChangedEvent event) {
        if (event.name().startsWith(BlockPlacement.CONFIG_PREFIX + ".")) {
            invalidator.invalidate(List.of(BlockPlacement.LIST_CACHE_TAG));
        }
    }
}
