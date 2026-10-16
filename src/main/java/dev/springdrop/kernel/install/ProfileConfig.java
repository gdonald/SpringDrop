package dev.springdrop.kernel.install;

import dev.springdrop.kernel.config.sync.ConfigDependencyCalculator;
import dev.springdrop.kernel.config.sync.ConfigFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads a profile's config files, from each of its locations in turn, a later
 * file replacing an earlier one of the same name: each object's value as YAML, in an order
 * that puts every object after the objects it depends on, those its name
 * implies and those its file declares under {@code dependencies.config}.
 */
@Component
public class ProfileConfig {

    private final ResourcePatternResolver resources;
    private final ConfigFiles files;
    private final ConfigDependencyCalculator dependencies;

    public ProfileConfig(ResourcePatternResolver resources, ConfigFiles files,
            ConfigDependencyCalculator dependencies) {
        this.resources = resources;
        this.files = files;
        this.dependencies = dependencies;
    }

    public Map<String, String> objects(InstallProfile profile) {
        Map<String, JsonNode> documents = new LinkedHashMap<>();
        try {
            for (String location : profile.configLocations()) {
                for (Resource resource : resources.getResources(location)) {
                    String filename = String.valueOf(resource.getFilename());
                    documents.put(filename.substring(0, filename.length() - ConfigFiles.EXTENSION.length()),
                            files.parse(resource.getContentAsString(StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        Set<String> visiting = new LinkedHashSet<>();
        documents.keySet().stream().sorted().forEach(name -> place(name, documents, visiting, ordered));
        return ordered;
    }

    private void place(String name, Map<String, JsonNode> documents, Set<String> visiting,
            Map<String, String> ordered) {
        if (!documents.containsKey(name) || !visiting.add(name)) {
            return;
        }
        List<String> needed = new ArrayList<>(dependencies.dependencies(name).config());
        documents.get(name).path("dependencies").path("config").forEach(dependency -> needed.add(
                dependency.asString()));
        needed.forEach(dependency -> place(dependency, documents, visiting, ordered));
        ordered.put(name, files.toYaml(documents.get(name).get("data")));
    }
}
