package dev.springdrop.kernel.views;

import org.springframework.security.core.Authentication;

/**
 * Who may see a view. An access plugin is a plugin registered with
 * {@code @SpringDropPlugin(type = AccessPlugin.class)}.
 */
public interface AccessPlugin extends ViewPlugin {

    boolean allows(PluginConfig config, Authentication authentication);
}
