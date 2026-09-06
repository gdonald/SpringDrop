package dev.springdrop.kernel.entity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * The registry of entity type definitions, and the way to reach the handlers a
 * type names. Definitions come from every {@link EntityTypeProvider} on the
 * classpath, so a module adds a type without core listing it.
 */
@Component
public class EntityTypeManager {

    private final Map<String, EntityType> types;
    private final ApplicationContext context;

    public EntityTypeManager(List<EntityTypeProvider> providers, ApplicationContext context) {
        this.types = providers.stream()
                .flatMap(provider -> provider.entityTypes().stream())
                .collect(Collectors.toMap(
                        entityType -> entityType.id(), Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));
        this.context = context;
    }

    public Optional<EntityType> find(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public EntityType require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("No entity type registered with id '" + id + "'"));
    }

    public Collection<EntityType> all() {
        return types.values();
    }

    public EntityStorage storageFor(String id) {
        return context.getBean(require(id).storageHandler());
    }

    public EntityAccessHandler accessHandlerFor(String id) {
        return context.getBean(require(id).accessHandler());
    }

    /** Creates the storage the type needs, the step a module runs on install. */
    public void installStorage(String id) {
        EntityType type = require(id);
        storageFor(id).install(type);
    }

    public void uninstallStorage(String id) {
        EntityType type = require(id);
        storageFor(id).uninstall(type);
    }
}
