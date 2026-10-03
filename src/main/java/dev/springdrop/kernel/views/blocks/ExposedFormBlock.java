package dev.springdrop.kernel.views.blocks;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewPaths;
import dev.springdrop.kernel.views.ViewRenderer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The exposed form of a page display set to draw its form in a block, leading
 * to the page with the reader's choices.
 */
public class ExposedFormBlock implements BlockPlugin {

    private final ViewConfig view;
    private final ViewDisplay display;
    private final ViewRenderer renderer;
    private final Supplier<Map<String, String>> input;

    public ExposedFormBlock(ViewConfig view, ViewDisplay display, ViewRenderer renderer,
            Supplier<Map<String, String>> input) {
        this.view = view;
        this.display = display;
        this.renderer = renderer;
        this.input = input;
    }

    @Override
    public String label() {
        return "Exposed form: " + view.label() + ": " + (display.title().isEmpty() ? display.id() : display.title());
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        String form = renderer.exposedForm(view, display.id(), input.get(), ViewPaths.address(display),
                ViewRenderer.elementId(view, display.id()));
        return form.isEmpty() ? Optional.empty() : Optional.of(Renderable.of("markup").with("value", form));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY.withMaxAge(Duration.ZERO);
    }
}
