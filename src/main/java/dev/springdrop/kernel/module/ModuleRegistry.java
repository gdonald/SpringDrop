package dev.springdrop.kernel.module;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The loaded module descriptors and their resolved install order. Built once at
 * startup from the descriptors on the classpath.
 */
@Component
public class ModuleRegistry {

    private final Map<String, ModuleInfo> modules;
    private final List<ModuleInfo> installOrder;

    public ModuleRegistry(ModuleDescriptorLoader loader, ModuleDependencyResolver resolver) {
        List<ModuleInfo> loaded = loader.load();
        this.modules = loaded.stream().collect(Collectors.toMap(
                ModuleInfo::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.installOrder = resolver.resolveInstallOrder(loaded);
    }

    public Optional<ModuleInfo> find(String name) {
        return Optional.ofNullable(modules.get(name));
    }

    public Collection<ModuleInfo> all() {
        return modules.values();
    }

    public List<ModuleInfo> installOrder() {
        return installOrder;
    }
}
