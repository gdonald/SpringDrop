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

    private final Class<T> contract;
    private final Map<String, Supplier<?>> plugins;

    PluginManager(Class<T> contract, Map<String, Supplier<?>> plugins) {
        this.contract = contract;
        this.plugins = plugins;
    }

    /**
     * The same plugins seen as another contract. The registry keeps one manager
     * per contract and hands it back typed through this, which shares the index
     * rather than copying it. Each instance is checked against the contract as
     * it is resolved.
     */
    <R> PluginManager<R> as(Class<R> other) {
        return new PluginManager<>(other, plugins);
    }

    public T get(String id) {
        Supplier<?> supplier = plugins.get(id);
        if (supplier == null) {
            throw new IllegalArgumentException("No plugin registered with id '" + id + "'");
        }
        return contract.cast(supplier.get());
    }

    public boolean has(String id) {
        return plugins.containsKey(id);
    }

    public Set<String> ids() {
        return plugins.keySet();
    }
}
