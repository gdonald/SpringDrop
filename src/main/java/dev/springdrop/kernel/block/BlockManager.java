package dev.springdrop.kernel.block;

import dev.springdrop.kernel.block.plugins.FieldBlockDeriver;
import dev.springdrop.kernel.config.ConfigChangedEvent;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
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

    /**
     * The blocks a theme region can hold, listed by label. Field blocks draw a
     * part of the entity a layout belongs to, so they are left out here.
     */
    public List<BlockDefinition> definitions() {
        return listed(id -> !isFieldBlock(id));
    }

    /** The blocks a layout of one bundle can hold: the theme's, and the fields of that bundle. */
    public List<BlockDefinition> layoutDefinitions(String entityTypeId, String bundle) {
        String ownFields = FieldBlockDeriver.bundlePrefix(entityTypeId, bundle);
        return listed(id -> !isFieldBlock(id) || id.startsWith(ownFields));
    }

    private List<BlockDefinition> listed(Predicate<String> included) {
        PluginManager<BlockPlugin> plugins = plugins();
        return plugins.ids().stream()
                .filter(included)
                .map(id -> new BlockDefinition(id, plugins.get(id).label()))
                .sorted(Comparator.comparing(BlockDefinition::label).thenComparing(BlockDefinition::id))
                .toList();
    }

    public boolean has(String id) {
        return plugins().has(id);
    }

    /** Whether a theme region can hold the block, which a field block it cannot. */
    public boolean placeableInThemes(String id) {
        return has(id) && !isFieldBlock(id);
    }

    private static boolean isFieldBlock(String id) {
        return id.startsWith(FieldBlockDeriver.ID + ":");
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
