package dev.springdrop.kernel.permission;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Every permission the site knows about, gathered from the modules that declare
 * them. The roles admin lists these, and a route naming one is gated by it.
 *
 * <p>Providers are asked on every lookup, so a provider that builds permissions
 * from what the site stores, such as one per content type, offers a new one as
 * soon as it is saved.
 */
@Component
public class PermissionRegistry {

    private final List<PermissionProvider> providers;

    public PermissionRegistry(List<PermissionProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    private Map<String, PermissionDefinition> permissions() {
        Map<String, PermissionDefinition> permissions = new LinkedHashMap<>();
        providers.stream()
                .flatMap(provider -> provider.permissions().stream())
                .forEach(permission -> permissions.putIfAbsent(permission.name(), permission));
        return permissions;
    }

    public Collection<PermissionDefinition> all() {
        return List.copyOf(permissions().values());
    }

    public Optional<PermissionDefinition> find(String name) {
        return Optional.ofNullable(permissions().get(name));
    }

    public boolean has(String name) {
        return permissions().containsKey(name);
    }

    /** The permissions one module declares, in the order it declares them. */
    public List<PermissionDefinition> providedBy(String provider) {
        return permissions().values().stream()
                .filter(permission -> permission.provider().equals(provider))
                .toList();
    }

    /** The permissions the admin warns about before handing them out. */
    public List<PermissionDefinition> restricted() {
        return permissions().values().stream().filter(permission -> permission.restricted()).toList();
    }
}
