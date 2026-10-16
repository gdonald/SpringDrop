package dev.springdrop.kernel.install;

/** One thing the installer checks before it goes ahead: what it is, what was found, and whether that will do. */
public record InstallRequirement(String title, String found, boolean met, String help) {
}
