package dev.springdrop.kernel.module;

import java.util.List;

/**
 * A module's descriptor, read from its {@code module.yml}: machine name, label,
 * description, version, the machine names of the modules it depends on, and
 * the package the modules page lists it under, {@value #OTHER} when it names none.
 */
public record ModuleInfo(
        String name,
        String label,
        String description,
        String version,
        List<String> dependencies,
        String packageName) {

    public static final String OTHER = "Other";

    public ModuleInfo {
        dependencies = List.copyOf(dependencies);
        packageName = packageName == null || packageName.isBlank() ? OTHER : packageName;
    }

    public ModuleInfo(String name, String label, String description, String version, List<String> dependencies) {
        this(name, label, description, version, dependencies, OTHER);
    }
}
