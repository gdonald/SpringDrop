package dev.springdrop.kernel.views;

import dev.springdrop.kernel.theme.Pager;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * How many results a view shows at once, and the links between pages. A pager
 * is a plugin registered with {@code @SpringDropPlugin(type = PagerPlugin.class)}.
 */
public interface PagerPlugin extends ViewPlugin {

    String ITEMS_PER_PAGE = "items_per_page";

    String OFFSET = "offset";

    /** How many results one page shows, or 0 for all of them. */
    default int itemsPerPage(PluginConfig config) {
        return Math.max(0, config.number(ITEMS_PER_PAGE, 10));
    }

    /** How many results are skipped before the first page. */
    default int offset(PluginConfig config) {
        return Math.max(0, config.number(OFFSET, 0));
    }

    /** Whether the reader moves between pages; a pager that does not always shows the first. */
    boolean pages();

    /** The links between pages, or nothing when there is one page or the pager shows none. */
    Optional<Pager> links(int page, int totalPages, IntFunction<String> url);
}
