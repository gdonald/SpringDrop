package dev.springdrop.kernel.update;

/** Published when a module is installed, so its update routines count as applied: it starts current. */
public record ModuleInstalledEvent(String module) {
}
