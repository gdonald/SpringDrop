package dev.springdrop.kernel.routing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Indexes the {@link EntityUpcaster}s by target type so the argument resolver can
 * look up a loader for a controller parameter's type.
 */
@Component
public class EntityUpcastRegistry {

    private final Map<Class<?>, EntityUpcaster<?>> upcasters;

    public EntityUpcastRegistry(List<EntityUpcaster<?>> upcasters) {
        this.upcasters = upcasters.stream()
                .collect(Collectors.toMap(EntityUpcaster::entityType, Function.identity()));
    }

    public boolean supports(Class<?> type) {
        return upcasters.containsKey(type);
    }

    public Optional<?> load(Class<?> type, String id) {
        return upcasters.get(type).load(id);
    }
}
