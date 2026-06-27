package dev.springdrop.kernel.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Orders modules so that every module comes after the modules it depends on, the
 * order used for install, migration, and event dispatch. Rejects a missing
 * dependency or a dependency cycle.
 */
@Component
public class ModuleDependencyResolver {

    public List<ModuleInfo> resolveInstallOrder(Collection<ModuleInfo> modules) {
        Map<String, ModuleInfo> byName = modules.stream()
                .collect(Collectors.toMap(ModuleInfo::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        for (ModuleInfo module : modules) {
            for (String dependency : module.dependencies()) {
                if (!byName.containsKey(dependency)) {
                    throw new ModuleDependencyException(
                            "Module '" + module.name() + "' depends on missing module '" + dependency + "'");
                }
            }
        }

        List<ModuleInfo> ordered = new ArrayList<>();
        Set<String> resolved = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (ModuleInfo module : modules) {
            visit(module, byName, resolved, visiting, ordered);
        }
        return ordered;
    }

    private void visit(
            ModuleInfo module,
            Map<String, ModuleInfo> byName,
            Set<String> resolved,
            Set<String> visiting,
            List<ModuleInfo> ordered) {

        if (resolved.contains(module.name())) {
            return;
        }
        if (!visiting.add(module.name())) {
            throw new ModuleDependencyException("Circular dependency involving module '" + module.name() + "'");
        }
        for (String dependency : module.dependencies()) {
            visit(byName.get(dependency), byName, resolved, visiting, ordered);
        }
        visiting.remove(module.name());
        resolved.add(module.name());
        ordered.add(module);
    }
}
