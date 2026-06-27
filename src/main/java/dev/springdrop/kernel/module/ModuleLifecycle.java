package dev.springdrop.kernel.module;

/**
 * A module's install lifecycle hooks. A module provides one implementation,
 * named by {@link #moduleName()}. Install creates schema and seeds config;
 * uninstall reverses it. Hooks a module does not need are left empty.
 */
public interface ModuleLifecycle {

    String moduleName();

    void onInstall();

    void onEnable();

    void onDisable();

    void onUninstall();
}
