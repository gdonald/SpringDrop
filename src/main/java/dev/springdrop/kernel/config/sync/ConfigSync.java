package dev.springdrop.kernel.config.sync;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.module.ModuleManager;
import dev.springdrop.kernel.tx.TransactionRunner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Exports the site's config to a sync directory and imports it back. An export
 * writes every object, config entities and simple config alike, and removes
 * the files of objects that are gone. An import compares the directory with
 * the site: a file whose object is not on the site is created, one whose value
 * differs is updated, an object with no file is deleted, and a file under a
 * new name carrying the uuid of an object on the site renames that object.
 * The changes are applied in one transaction, in dependency order, so an
 * import that fails leaves the site's config as it was.
 *
 * <p>An import is checked before anything is applied, and refused whole when
 * a file depends on a config object the import does not hold or on a module
 * that is not enabled, when two files carry the same uuid, or when objects
 * depend on each other, or when a file's value does not match its schema. A
 * file's dependencies are those it declares together with those its name
 * implies, so a file edited by hand cannot drop them.
 */
@Component
public class ConfigSync {

    private final ConfigStore config;
    private final ConfigFiles files;
    private final ConfigDependencyCalculator dependencies;
    private final List<ConfigSyncHandler> handlers;
    private final TransactionRunner transactions;
    private final ModuleManager modules;
    private final ConfigSchemas schemas;

    public ConfigSync(ConfigStore config, ConfigFiles files, ConfigDependencyCalculator dependencies,
            List<ConfigSyncHandler> handlers, TransactionRunner transactions, ModuleManager modules,
            ConfigSchemas schemas) {
        this.config = config;
        this.files = files;
        this.dependencies = dependencies;
        this.handlers = List.copyOf(handlers);
        this.transactions = transactions;
        this.modules = modules;
        this.schemas = schemas;
    }

    /** One object as an export writes it, or nothing when the site has no such object. */
    public Optional<String> exportSingle(String name) {
        return Optional.ofNullable(config.everything().get(name)).map(stored -> files.document(
                new ConfigFile(name, stored.uuid(), dependencies.dependencies(name), stored.data())));
    }

    /**
     * Imports one object from its value as YAML, creating it or replacing the
     * value of the object of that name, which keeps its uuid. The import is
     * refused, with every reason, when the YAML cannot be read, the value does
     * not match the object's schema, or the object depends on config the site
     * does not have or on a module that is not enabled.
     */
    public List<String> importSingle(String name, String yaml) {
        JsonNode data;
        try {
            data = files.parse(yaml);
        } catch (ConfigFileException unreadable) {
            return List.of(unreadable.getMessage());
        }
        Map<String, ConfigStore.StoredConfig> active = config.everything();
        List<String> problems = new ArrayList<>();
        schemas.violation(name, data).ifPresent(problems::add);
        ConfigDependencies needed = dependencies.dependencies(name);
        needed.config().stream().filter(dependency -> !active.containsKey(dependency)).forEach(dependency ->
                problems.add(name + " depends on " + dependency + ", which the site does not have."));
        needed.module().stream().filter(module -> !modules.isEnabled(module)).forEach(module ->
                problems.add(name + " depends on the module " + module + ", which is not enabled."));
        if (problems.isEmpty()) {
            transactions.run(() -> {
                ConfigStore.StoredConfig current = active.get(name);
                if (current == null) {
                    handlerFor(name).ifPresent(handler -> handler.create(name, data));
                }
                config.saveImported(name, data, current == null ? UUID.randomUUID() : current.uuid());
            });
        }
        return List.copyOf(problems);
    }

    public void export(Path directory) {
        files.clear(directory);
        config.everything().forEach((name, stored) -> files.write(directory,
                new ConfigFile(name, stored.uuid(), dependencies.dependencies(name), stored.data())));
    }

    /** What an import of the directory would change, creations and updates first and deletions last. */
    public List<ConfigChange> changes(Path directory) {
        return changes(files.read(directory), config.everything());
    }

    /** The object on the site and in the directory, as YAML, for showing what an import would change. */
    public Optional<String> activeYaml(String name) {
        return Optional.ofNullable(config.everything().get(name)).map(stored -> files.toYaml(stored.data()));
    }

    public Optional<String> stagedYaml(Path directory, String name) {
        return Optional.ofNullable(files.read(directory).get(name)).map(file -> files.toYaml(file.data()));
    }

    /** Why an import of the directory would be refused, or nothing when it would go ahead. */
    public List<String> problems(Path directory) {
        return problems(files.read(directory));
    }

    /** Applies every change the directory holds, all of them or none. */
    public void apply(Path directory) {
        Map<String, ConfigFile> staged = files.read(directory);
        List<String> problems = problems(staged);
        if (!problems.isEmpty()) {
            throw new ConfigImportException(problems);
        }
        transactions.run(() -> {
            for (ConfigChange change : changes(staged, config.everything())) {
                switch (change.operation()) {
                    case CREATE -> {
                        ConfigFile file = staged.get(change.name());
                        handlerFor(change.name()).ifPresent(handler -> handler.create(change.name(), file.data()));
                        config.saveImported(change.name(), file.data(), file.uuid());
                    }
                    case UPDATE -> config.saveImported(change.name(), staged.get(change.name()).data(),
                            staged.get(change.name()).uuid());
                    case RENAME -> {
                        config.rename(change.previousName(), change.name());
                        config.saveImported(change.name(), staged.get(change.name()).data(),
                                staged.get(change.name()).uuid());
                    }
                    default -> handlerFor(change.name()).ifPresentOrElse(handler -> handler.delete(change.name()),
                            () -> config.delete(change.name()));
                }
            }
        });
    }

    private List<String> problems(Map<String, ConfigFile> staged) {
        List<String> problems = new ArrayList<>();
        Map<UUID, String> byUuid = new HashMap<>();
        staged.forEach((name, file) -> {
            String other = byUuid.putIfAbsent(file.uuid(), name);
            if (other != null) {
                problems.add(other + " and " + name + " carry the same uuid.");
            }
        });
        staged.keySet().forEach(name -> {
            for (String dependency : configDependencies(staged, name)) {
                if (!staged.containsKey(dependency)) {
                    problems.add(name + " depends on " + dependency + ", which the import does not hold.");
                }
            }
            for (String module : moduleDependencies(staged, name)) {
                if (!modules.isEnabled(module)) {
                    problems.add(name + " depends on the module " + module + ", which is not enabled.");
                }
            }
        });
        staged.forEach((name, file) -> schemas.violation(name, file.data()).ifPresent(problems::add));
        Set<String> finished = new HashSet<>();
        for (String name : staged.keySet()) {
            findCycle(name, staged, new ArrayList<>(), finished).ifPresent(cycle -> {
                finished.addAll(cycle);
                problems.add("The objects " + String.join(", ", cycle) + " depend on each other.");
            });
        }
        return List.copyOf(problems);
    }

    private Set<String> configDependencies(Map<String, ConfigFile> staged, String name) {
        Set<String> all = new LinkedHashSet<>(staged.get(name).dependencies().config());
        all.addAll(dependencies.dependencies(name).config());
        return all;
    }

    private Set<String> moduleDependencies(Map<String, ConfigFile> staged, String name) {
        Set<String> all = new LinkedHashSet<>(staged.get(name).dependencies().module());
        all.addAll(dependencies.dependencies(name).module());
        return all;
    }

    /** A chain of dependencies leading from the object back to an object already on the path. */
    private Optional<List<String>> findCycle(String name, Map<String, ConfigFile> staged, List<String> path,
            Set<String> finished) {
        if (path.contains(name)) {
            return Optional.of(List.copyOf(path.subList(path.indexOf(name), path.size())));
        }
        if (!staged.containsKey(name) || finished.contains(name)) {
            return Optional.empty();
        }
        path.add(name);
        for (String dependency : configDependencies(staged, name)) {
            Optional<List<String>> cycle = findCycle(dependency, staged, path, finished);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        path.removeLast();
        finished.add(name);
        return Optional.empty();
    }

    private Optional<ConfigSyncHandler> handlerFor(String name) {
        return handlers.stream().filter(handler -> handler.handles(name)).findFirst();
    }

    private List<ConfigChange> changes(Map<String, ConfigFile> staged, Map<String, ConfigStore.StoredConfig> active) {
        Map<UUID, String> activeByUuid = new HashMap<>();
        active.forEach((name, stored) -> activeByUuid.put(stored.uuid(), name));
        Map<String, ConfigChange> writes = new LinkedHashMap<>();
        Set<String> renamed = new LinkedHashSet<>();
        staged.forEach((name, file) -> {
            ConfigStore.StoredConfig current = active.get(name);
            String previous = activeByUuid.get(file.uuid());
            if (current != null) {
                if (!current.data().equals(file.data()) || !current.uuid().equals(file.uuid())) {
                    writes.put(name, ConfigChange.of(ConfigChange.Operation.UPDATE, name));
                }
            } else if (previous != null && !staged.containsKey(previous)) {
                writes.put(name, new ConfigChange(ConfigChange.Operation.RENAME, name, previous));
                renamed.add(previous);
            } else {
                writes.put(name, ConfigChange.of(ConfigChange.Operation.CREATE, name));
            }
        });
        Map<String, ConfigChange> deletes = new LinkedHashMap<>();
        active.keySet().stream().filter(name -> !staged.containsKey(name) && !renamed.contains(name))
                .forEach(name -> deletes.put(name, ConfigChange.of(ConfigChange.Operation.DELETE, name)));

        List<ConfigChange> ordered = new ArrayList<>(dependencyOrder(writes,
                name -> List.copyOf(configDependencies(staged, name))));
        ordered.addAll(dependencyOrder(deletes, name -> dependencies.dependencies(name).config()).reversed());
        return List.copyOf(ordered);
    }

    /** The changes with each one after the changes to the objects it depends on. */
    private static List<ConfigChange> dependencyOrder(Map<String, ConfigChange> changes,
            Function<String, List<String>> dependenciesOf) {
        List<ConfigChange> ordered = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        changes.keySet().forEach(name -> place(name, changes, dependenciesOf, placed, ordered));
        return ordered;
    }

    private static void place(String name, Map<String, ConfigChange> changes,
            Function<String, List<String>> dependenciesOf, Set<String> placed, List<ConfigChange> ordered) {
        if (!changes.containsKey(name) || !placed.add(name)) {
            return;
        }
        dependenciesOf.apply(name).forEach(dependency -> place(dependency, changes, dependenciesOf, placed, ordered));
        ordered.add(changes.get(name));
    }
}
