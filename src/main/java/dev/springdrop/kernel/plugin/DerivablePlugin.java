package dev.springdrop.kernel.plugin;

import java.util.Map;

/**
 * A plugin that expands into several derivative instances, each registered under
 * {@code baseId:suffix} (for example a menu block deriving one instance per menu).
 * Implemented by a {@link SpringDropPlugin} that wants the derivative pattern.
 */
public interface DerivablePlugin<T> {

    Map<String, T> derivatives();
}
