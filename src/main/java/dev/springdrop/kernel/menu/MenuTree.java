package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.render.CacheMetadata;
import java.util.List;

/**
 * A built menu: its top-level items, each carrying its own children, and what
 * the result depends on. The cacheability says which tags invalidate the tree
 * and which contexts it varies by, since access filtering makes one visitor's
 * tree differ from another's.
 */
public record MenuTree(String menu, List<MenuTreeItem> items, CacheMetadata cacheability) {

    public MenuTree {
        items = List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
