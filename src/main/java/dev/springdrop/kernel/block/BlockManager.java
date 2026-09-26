package dev.springdrop.kernel.block;

import dev.springdrop.kernel.config.ConfigChangedEvent;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The block plugins the site has, as the block library lists them. The menu
 * block derives one block per menu, so adding or removing a menu drops the
 * cached plugins and the next lookup derives them again.
 */
@Component
public class BlockManager {

    private final PluginRegistry registry;

    public BlockManager(PluginRegistry registry) {
        this.registry = registry;
    }

    /** Every block plugin, listed by label. */
    public List<BlockDefinition> definitions() {
        PluginManager<BlockPlugin> plugins = plugins();
        return plugins.ids().stream()
                .map(id -> new BlockDefinition(id, plugins.get(id).label()))
                .sorted(Comparator.comparing(BlockDefinition::label).thenComparing(BlockDefinition::id))
                .toList();
    }

    public boolean has(String id) {
        return plugins().has(id);
    }

    public BlockPlugin plugin(String id) {
        return plugins().get(id);
    }

    @EventListener
    void menusChanged(ConfigChangedEvent event) {
        if (event.name().startsWith(MenuConfig.CONFIG_PREFIX + ".")) {
            registry.invalidate(BlockPlugin.class);
        }
    }

    private PluginManager<BlockPlugin> plugins() {
        return registry.managerFor(BlockPlugin.class);
    }
}
