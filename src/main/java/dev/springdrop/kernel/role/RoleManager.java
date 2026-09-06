package dev.springdrop.kernel.role;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.permission.PermissionRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The site's roles and what they let people do. Two roles always exist:
 * anonymous, held by everyone who has not signed in, and authenticated, held by
 * everyone who has.
 */
@Component
public class RoleManager {

    private final ConfigStore configStore;
    private final PermissionRegistry permissionRegistry;

    public RoleManager(ConfigStore configStore, PermissionRegistry permissionRegistry) {
        this.configStore = configStore;
        this.permissionRegistry = permissionRegistry;
    }

    /** Creates the two roles a site always has, leaving any that are there alone. */
    public void install() {
        reserve(RoleConfig.ANONYMOUS, "Anonymous visitor", 0);
        reserve(RoleConfig.AUTHENTICATED, "Signed-in visitor", 1);
    }

    public void save(RoleConfig role) {
        configStore.save(RoleConfig.configName(role.id()), role);
    }

    public Optional<RoleConfig> find(String id) {
        return Optional.ofNullable(configStore.read(RoleConfig.configName(id), RoleConfig.class, null));
    }

    public void delete(String id) {
        configStore.delete(RoleConfig.configName(id));
    }

    /** Every role, in the order a site lists them. */
    public List<RoleConfig> all() {
        List<RoleConfig> roles = new ArrayList<>();
        for (String name : configStore.listNames(RoleConfig.CONFIG_PREFIX)) {
            find(name.substring(RoleConfig.CONFIG_PREFIX.length() + 1)).ifPresent(roles::add);
        }
        return roles.stream()
                .sorted(Comparator.comparingInt((RoleConfig role) -> role.weight())
                        .thenComparing(role -> role.id()))
                .toList();
    }

    /**
     * The permissions someone holding these roles has. An administrator role
     * carries everything the site knows about, so a permission added later is
     * covered without anyone going back to tick it.
     */
    public List<String> permissionsOf(List<String> roleIds) {
        Set<String> permissions = new LinkedHashSet<>();
        for (String roleId : roleIds) {
            find(roleId).ifPresent(role -> {
                if (role.administrator()) {
                    permissionRegistry.all().stream()
                            .map(permission -> permission.name())
                            .forEach(permissions::add);
                } else {
                    permissions.addAll(role.permissions());
                }
            });
        }
        return List.copyOf(permissions);
    }

    public void grant(String roleId, String permission) {
        find(roleId).ifPresent(role -> save(role.granting(permission)));
    }

    public void revoke(String roleId, String permission) {
        find(roleId).ifPresent(role -> save(role.revoking(permission)));
    }

    private void reserve(String id, String label, int weight) {
        if (find(id).isEmpty()) {
            save(RoleConfig.of(id, label, weight));
        }
    }
}
