package dev.springdrop.kernel.views.blocks;

import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.config.ConfigChangedEvent;
import dev.springdrop.kernel.plugin.DerivablePlugin;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.ViewCache;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewExecutor;
import dev.springdrop.kernel.views.ViewManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;

/**
 * Derives a block for every block display, as {@code views_block:<view>-<display>}.
 * Changing a view drops the derived blocks, so they follow the views.
 */
@SpringDropPlugin(id = ViewBlockDeriver.ID, type = BlockPlugin.class)
public class ViewBlockDeriver implements DerivablePlugin<BlockPlugin> {

    public static final String ID = "views_block";

    private final ViewManager views;
    private final ViewExecutor executor;
    private final ViewCache cache;
    private final RequestInput input;
    private final PluginRegistry registry;

    public ViewBlockDeriver(ViewManager views, ViewExecutor executor, ViewCache cache, RequestInput input,
            PluginRegistry registry) {
        this.views = views;
        this.executor = executor;
        this.cache = cache;
        this.input = input;
        this.registry = registry;
    }

    @Override
    public Map<String, BlockPlugin> derivatives() {
        Map<String, BlockPlugin> blocks = new LinkedHashMap<>();
        for (ViewConfig view : views.all()) {
            for (ViewDisplay display : view.displays()) {
                if (display.plugin().equals(ViewDisplay.BLOCK)) {
                    blocks.put(view.id() + "-" + display.id(),
                            new ViewBlock(view, display, executor, cache, input::current));
                }
            }
        }
        return blocks;
    }

    @EventListener
    void viewsChanged(ConfigChangedEvent event) {
        if (event.name().startsWith(ViewConfig.CONFIG_PREFIX + ".")) {
            registry.invalidate(BlockPlugin.class);
        }
    }
}
