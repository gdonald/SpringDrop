package dev.springdrop.kernel.permission;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads the permissions modules declare in {@code springdrop/permissions/*.permissions.yml}.
 * Each entry is keyed by the permission name and carries its title, an optional
 * description, and whether it is restricted:
 *
 * <pre>
 * administer nodes:
 *   title: Administer content
 *   description: Edit and delete any content on the site.
 *   restricted: true
 * </pre>
 */
@Component
public class PermissionFileLoader implements PermissionProvider {

    static final String LOCATION_PATTERN = "classpath*:springdrop/permissions/*.permissions.yml";

    private static final String TITLE = "title";

    private static final String DESCRIPTION = "description";

    private static final String RESTRICTED = "restricted";

    private final ResourcePatternResolver resourcePatternResolver;

    public PermissionFileLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public List<PermissionDefinition> permissions() {
        List<PermissionDefinition> permissions = new ArrayList<>();
        for (Resource resource : declarations()) {
            permissions.addAll(parse(resource));
        }
        return List.copyOf(permissions);
    }

    private Resource[] declarations() {
        try {
            return resourcePatternResolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan for permission declarations", e);
        }
    }

    private static List<PermissionDefinition> parse(Resource resource) {
        String provider = providerOf(resource);
        Map<String, Object> declared = read(resource);

        List<PermissionDefinition> permissions = new ArrayList<>();
        declared.forEach((name, value) -> {
            Map<String, Object> details = asMap(value);
            permissions.add(new PermissionDefinition(
                    name,
                    String.valueOf(details.getOrDefault(TITLE, name)),
                    String.valueOf(details.getOrDefault(DESCRIPTION, "")),
                    Boolean.TRUE.equals(details.get(RESTRICTED)),
                    provider));
        });
        return permissions;
    }

    private static Map<String, Object> read(Resource resource) {
        try {
            Map<String, Object> declared =
                    new Yaml().load(resource.getContentAsString(StandardCharsets.UTF_8));
            return (declared == null) ? Map.of() : declared;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read permission declarations: " + resource.getDescription(), e);
        }
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> declared) {
            declared.forEach((key, entry) -> details.put(String.valueOf(key), entry));
        }
        return details;
    }

    /** The module a declaration file belongs to, from its file name. */
    private static String providerOf(Resource resource) {
        String filename = resource.getFilename();
        return (filename == null) ? "" : filename.replace(".permissions.yml", "");
    }
}
