package dev.springdrop.kernel.config.sync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads and writes the YAML files of a sync directory, one per config object,
 * named {@code <name>.yml}. A file that is not a config object, whose uuid is
 * missing or malformed, or that has no data is refused with the file named.
 */
@Component
public class ConfigFiles {

    public static final String EXTENSION = ".yml";

    private final YAMLMapper yaml = YAMLMapper.builder().build();

    /** Every config file in the directory, by name in name order. A directory that is not there holds none. */
    public Map<String, ConfigFile> read(Path directory) {
        Map<String, ConfigFile> files = new LinkedHashMap<>();
        for (Path path : yamlFiles(directory)) {
            String name = path.getFileName().toString();
            name = name.substring(0, name.length() - EXTENSION.length());
            files.put(name, read(name, path));
        }
        return files;
    }

    /**
     * One object's file. A deleted object has no file, so that is not an error;
     * a file that cannot be read is.
     */
    public void read(Path directory, String name) {
        Path path = directory.resolve(name + EXTENSION);
        if (Files.exists(path)) {
            read(name, path);
        }
    }

    public void write(Path directory, ConfigFile file) {
        io(() -> Files.createDirectories(directory));
        io(() -> Files.writeString(directory.resolve(file.name() + EXTENSION), document(file)));
    }

    /** The file's text, as an export writes it. */
    public String document(ConfigFile file) {
        ObjectNode document = yaml.createObjectNode();
        document.put("uuid", file.uuid().toString());
        document.set("dependencies", yaml.valueToTree(file.dependencies()));
        document.set("data", file.data());
        return yaml.writeValueAsString(document);
    }

    /** A value pasted as YAML. Blank text, or text that is not YAML, is refused. */
    public JsonNode parse(String text) {
        if (text.isBlank()) {
            throw new ConfigFileException("The YAML holds no value.");
        }
        try {
            return yaml.readTree(text);
        } catch (JacksonException unreadable) {
            throw new ConfigFileException("The YAML cannot be read.");
        }
    }

    /** Removes every config file from the directory, leaving anything else there. */
    public void clear(Path directory) {
        for (Path path : yamlFiles(directory)) {
            io(() -> Files.deleteIfExists(path));
        }
    }

    /** One object as YAML, for showing what an import would change. */
    public String toYaml(JsonNode data) {
        return yaml.writeValueAsString(data);
    }

    private ConfigFile read(String name, Path path) {
        JsonNode document;
        try {
            document = yaml.readTree(Files.readString(path));
        } catch (IOException | JacksonException unreadable) {
            throw new ConfigFileException(name + EXTENSION + " is not YAML.");
        }
        if (!(document instanceof ObjectNode) || !document.has("data")) {
            throw new ConfigFileException(name + EXTENSION + " has no data.");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(document.path("uuid").asString(""));
        } catch (IllegalArgumentException malformed) {
            throw new ConfigFileException(name + EXTENSION + " has no uuid.");
        }
        ConfigDependencies dependencies = document.has("dependencies")
                ? yaml.treeToValue(document.get("dependencies"), ConfigDependencies.class) : ConfigDependencies.NONE;
        return new ConfigFile(name, uuid, dependencies, document.get("data"));
    }

    private static List<Path> yamlFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        return io(() -> {
            try (Stream<Path> paths = Files.list(directory)) {
                return paths.filter(path -> path.getFileName().toString().endsWith(EXTENSION)).sorted().toList();
            }
        });
    }

    /** Filesystem work, whose failure is not one a caller can do anything about but report. */
    @FunctionalInterface
    private interface FileWork<T> {

        T run() throws IOException;
    }

    private static <T> T io(FileWork<T> work) {
        try {
            return work.run();
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
