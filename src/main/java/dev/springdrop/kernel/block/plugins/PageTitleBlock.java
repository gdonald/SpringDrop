package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import java.util.Map;
import java.util.Optional;

/** The title of the page being shown. */
@SpringDropPlugin(id = PageTitleBlock.ID, type = BlockPlugin.class)
public class PageTitleBlock implements BlockPlugin {

    public static final String ID = "page_title_block";

    @Override
    public String label() {
        return "Page title";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        if (context.title().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Renderable.of(BlockTemplates.ELEMENTS, "title").with("title", context.title()));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY.withContext(BlockTemplates.URL_PATH_CONTEXT);
    }
}
