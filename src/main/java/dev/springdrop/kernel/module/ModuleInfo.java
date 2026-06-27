package dev.springdrop.kernel.module;

import java.util.List;

/**
 * A module's descriptor, read from its {@code module.yml}: machine name, label,
 * description, version, and the machine names of the modules it depends on.
 */
public record ModuleInfo(
        String name,
        String label,
        String description,
        String version,
        List<String> dependencies) {
}
