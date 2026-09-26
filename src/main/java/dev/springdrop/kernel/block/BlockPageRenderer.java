package dev.springdrop.kernel.block;

import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.render.RenderedPage;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.PageRenderer;
import dev.springdrop.kernel.theme.ThemeRegistry;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Draws a page with the blocks placed in the active theme around its content.
 * When a main content block is placed, the content is drawn there and nowhere
 * else. When none is, the layout draws the content where it always has, so a
 * theme with no blocks placed still shows its pages.
 */
@Service
public class BlockPageRenderer {

    private final PageRenderer pages;
    private final BlockRegionBuilder regions;
    private final ThemeRegistry themes;

    public BlockPageRenderer(PageRenderer pages, BlockRegionBuilder regions, ThemeRegistry themes) {
        this.pages = pages;
        this.regions = regions;
        this.themes = themes;
    }

    public RenderedPage render(PageChrome chrome, Renderable content, BlockContext context) {
        PageRegions built = regions.build(themes.active().orElse(""), context.withMainContent(content));

        PageChrome withBlocks = chrome;
        for (Map.Entry<String, Renderable> region : built.regions().entrySet()) {
            withBlocks = withBlocks.withRegion(region.getKey(), region.getValue());
        }
        Renderable main = built.mainContentPlaced() ? Renderable.of("container") : content;
        return pages.render(withBlocks, main.cacheability(built.cacheability()));
    }
}
