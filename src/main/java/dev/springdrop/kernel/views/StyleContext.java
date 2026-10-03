package dev.springdrop.kernel.views;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * What a style plugin draws the results with: the results, the view's fields,
 * the style's settings, how one result is drawn by the row plugin, how one
 * field of one result is drawn, each field's heading, and the address that
 * sorts by a field, for a field the results can be sorted by.
 */
public record StyleContext(
        List<ResultRow> rows,
        List<HandlerConfig> fields,
        PluginConfig style,
        Function<ResultRow, String> row,
        BiFunction<ResultRow, HandlerConfig, String> field,
        Function<HandlerConfig, String> heading,
        Function<HandlerConfig, Optional<String>> sortUrl) {

    public StyleContext {
        rows = List.copyOf(rows);
        fields = List.copyOf(fields);
    }
}
