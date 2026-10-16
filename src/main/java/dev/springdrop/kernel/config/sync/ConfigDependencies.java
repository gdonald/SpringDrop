package dev.springdrop.kernel.config.sync;

import java.util.List;

/** What a config object needs in place before it: other config objects, by name, and modules. */
public record ConfigDependencies(List<String> config, List<String> module) {

    public static final ConfigDependencies NONE = new ConfigDependencies(List.of(), List.of());

    public ConfigDependencies {
        config = config == null ? List.of() : List.copyOf(config);
        module = module == null ? List.of() : List.copyOf(module);
    }
}
