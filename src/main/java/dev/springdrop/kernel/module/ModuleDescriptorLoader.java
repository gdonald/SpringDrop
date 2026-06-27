package dev.springdrop.kernel.module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Discovers module descriptors on the classpath at
 * {@code springdrop/modules/*.module.yml} and parses each into a {@link ModuleInfo}.
 */
@Component
public class ModuleDescriptorLoader {

    static final String LOCATION_PATTERN = "classpath*:springdrop/modules/*.module.yml";

    private final ResourcePatternResolver resourcePatternResolver;

    public ModuleDescriptorLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<ModuleInfo> load() {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            throw new ModuleDependencyException("Failed to scan for module descriptors", e);
        }
        List<ModuleInfo> modules = new ArrayList<>();
        for (Resource resource : resources) {
            modules.add(parse(resource));
        }
        return modules;
    }

    ModuleInfo parse(Resource resource) {
        String content;
        try {
            content = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModuleDependencyException("Failed to read module descriptor: " + resource.getDescription(), e);
        }
        Map<String, Object> data = new Yaml().load(content);
        @SuppressWarnings("unchecked")
        List<String> dependencies = (List<String>) data.getOrDefault("dependencies", List.of());
        return new ModuleInfo(
                (String) data.get("name"),
                (String) data.get("label"),
                (String) data.get("description"),
                String.valueOf(data.get("version")),
                List.copyOf(dependencies));
    }
}
