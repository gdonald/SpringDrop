package dev.springdrop.kernel.views.blocks;

import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.plugin.DerivablePlugin;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewRenderer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Derives a block for the exposed form of every page display set to draw its
 * form in a block, as {@code views_exposed_filter_block:<view>-<display>}.
 */
@SpringDropPlugin(id = ExposedFormBlockDeriver.ID, type = BlockPlugin.class)
public class ExposedFormBlockDeriver implements DerivablePlugin<BlockPlugin> {

    public static final String ID = "views_exposed_filter_block";

    private final ViewManager views;
    private final ViewRenderer renderer;
    private final RequestInput input;

    public ExposedFormBlockDeriver(ViewManager views, ViewRenderer renderer, RequestInput input) {
        this.views = views;
        this.renderer = renderer;
        this.input = input;
    }

    @Override
    public Map<String, BlockPlugin> derivatives() {
        Map<String, BlockPlugin> blocks = new LinkedHashMap<>();
        for (ViewConfig view : views.all()) {
            for (ViewDisplay display : view.displays()) {
                if (display.plugin().equals(ViewDisplay.PAGE) && display.flag(ViewDisplay.EXPOSED_BLOCK)) {
                    blocks.put(view.id() + "-" + display.id(),
                            new ExposedFormBlock(view, display, renderer, input::current));
                }
            }
        }
        return blocks;
    }
}
