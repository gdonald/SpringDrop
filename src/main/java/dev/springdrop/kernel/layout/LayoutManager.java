package dev.springdrop.kernel.layout;

import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** The layout plugins the site has, as the layout library lists them. */
@Component
public class LayoutManager {

    private final PluginRegistry registry;

    public LayoutManager(PluginRegistry registry) {
        this.registry = registry;
    }

    /** Every layout plugin, listed by label. */
    public List<LayoutDefinition> definitions() {
        PluginManager<LayoutPlugin> plugins = plugins();
        return plugins.ids().stream()
                .map(id -> new LayoutDefinition(id, plugins.get(id).label()))
                .sorted(Comparator.comparing(LayoutDefinition::label).thenComparing(LayoutDefinition::id))
                .toList();
    }

    public boolean has(String id) {
        return plugins().has(id);
    }

    public LayoutPlugin plugin(String id) {
        return plugins().get(id);
    }

    private PluginManager<LayoutPlugin> plugins() {
        return registry.managerFor(LayoutPlugin.class);
    }
}
