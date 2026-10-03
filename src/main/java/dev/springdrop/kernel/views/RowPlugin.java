package dev.springdrop.kernel.views;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * How one result is drawn inside a style: its fields, or the entity rendered
 * in a view mode. A row is a plugin registered with
 * {@code @SpringDropPlugin(type = RowPlugin.class)}.
 */
public interface RowPlugin extends ViewPlugin {

    String render(ResultRow row, ViewOptions options, PluginConfig config,
            BiFunction<ResultRow, HandlerConfig, String> field, Function<HandlerConfig, String> heading);
}
