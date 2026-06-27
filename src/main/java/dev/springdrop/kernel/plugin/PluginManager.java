package dev.springdrop.kernel.plugin;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The plugins of one contract type, indexed by id. Instances are resolved lazily
 * through the supplier captured at registration, so a base plugin is created only
 * when its id is first requested.
 */
public class PluginManager<T> {

    private final Map<String, Supplier<T>> plugins;

    PluginManager(Map<String, Supplier<T>> plugins) {
        this.plugins = plugins;
    }

    public T get(String id) {
        Supplier<T> supplier = plugins.get(id);
        if (supplier == null) {
            throw new IllegalArgumentException("No plugin registered with id '" + id + "'");
        }
        return supplier.get();
    }

    public boolean has(String id) {
        return plugins.containsKey(id);
    }

    public Set<String> ids() {
        return plugins.keySet();
    }
}
