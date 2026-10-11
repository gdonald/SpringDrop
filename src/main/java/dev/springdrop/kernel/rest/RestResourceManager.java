package dev.springdrop.kernel.rest;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionProvider;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The entity types served over REST, the formats they can be served in, and
 * the permission each enabled method of each resource needs.
 */
@Component
public class RestResourceManager implements PermissionProvider {

    public static final String PROVIDER = "rest";

    private final ConfigStore config;
    private final PluginRegistry plugins;

    public RestResourceManager(ConfigStore config, PluginRegistry plugins) {
        this.config = config;
        this.plugins = plugins;
    }

    public Optional<RestResourceConfig> find(String entityType) {
        return Optional.ofNullable(config.read(RestResourceConfig.configName(entityType), RestResourceConfig.class,
                null));
    }

    public List<RestResourceConfig> all() {
        return config.listNames(RestResourceConfig.CONFIG_PREFIX).stream()
                .map(name -> config.read(name, RestResourceConfig.class, null))
                .toList();
    }

    public void save(RestResourceConfig resource) {
        config.save(RestResourceConfig.configName(resource.entityType()), resource);
    }

    public void delete(String entityType) {
        config.delete(RestResourceConfig.configName(entityType));
    }

    /** The format ids serializers are registered under, in name order. */
    public List<String> formats() {
        return serializers().ids().stream().sorted().toList();
    }

    public Optional<RestSerializer> serializer(String format) {
        PluginManager<RestSerializer> serializers = serializers();
        return serializers.has(format) ? Optional.of(serializers.get(format)) : Optional.empty();
    }

    @Override
    public List<PermissionDefinition> permissions() {
        List<PermissionDefinition> permissions = new ArrayList<>();
        for (RestResourceConfig resource : all()) {
            for (String method : resource.methods()) {
                permissions.add(PermissionDefinition.of(RestResourceConfig.permission(method, resource.entityType()),
                        "Access " + method + " on the " + resource.entityType() + " resource", PROVIDER));
            }
        }
        return List.copyOf(permissions);
    }

    private PluginManager<RestSerializer> serializers() {
        return plugins.managerFor(RestSerializer.class);
    }
}
