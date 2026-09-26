package dev.springdrop.kernel.block;

import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import java.util.Map;

/**
 * The regions of one page with the blocks that show in them, what deciding
 * which blocks show depended on, and whether a main content block drew the
 * page's content, so the layout does not draw it a second time.
 */
public record PageRegions(Map<String, Renderable> regions, CacheMetadata cacheability, boolean mainContentPlaced) {

    public PageRegions {
        regions = Map.copyOf(regions);
    }
}
