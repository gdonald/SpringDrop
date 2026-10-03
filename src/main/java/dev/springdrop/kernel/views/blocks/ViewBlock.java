package dev.springdrop.kernel.views.blocks;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.views.ViewCache;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewExecutor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A block display of a view, drawing it on the page it is placed on with the
 * reader's choices from that page's address. A block whose view finds nothing,
 * or that the reader may not see, draws nothing. The view keeps its own cache,
 * so the block is drawn on every page.
 */
public class ViewBlock implements BlockPlugin {

    private final ViewConfig view;
    private final ViewDisplay display;
    private final ViewExecutor executor;
    private final ViewCache cache;
    private final Supplier<Map<String, String>> input;

    public ViewBlock(ViewConfig view, ViewDisplay display, ViewExecutor executor, ViewCache cache,
            Supplier<Map<String, String>> input) {
        this.view = view;
        this.display = display;
        this.executor = executor;
        this.cache = cache;
        this.input = input;
    }

    @Override
    public String label() {
        return view.label() + ": " + (display.title().isEmpty() ? display.id() : display.title());
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        Authentication reader = SecurityContextHolder.getContext().getAuthentication();
        if (!executor.mayAccess(view, display.id(), reader)) {
            return Optional.empty();
        }
        var rendered = cache.render(view, display.id(), List.of(), input.get(), context.path(), true, reader);
        return rendered.result().rows().isEmpty() ? Optional.empty()
                : Optional.of(Renderable.of("markup").with("value", rendered.html()));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY.withMaxAge(Duration.ZERO);
    }
}
