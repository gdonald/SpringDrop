package dev.springdrop.kernel.views;

import dev.springdrop.kernel.entity.query.Sort;

/**
 * Orders the results by a property. A sort handler is a plugin registered with
 * {@code @SpringDropPlugin(type = SortHandler.class)}.
 */
public interface SortHandler extends ViewPlugin {

    /** {@code asc} or {@code desc}. */
    String ORDER = "order";

    Sort sort(HandlerConfig config);
}
