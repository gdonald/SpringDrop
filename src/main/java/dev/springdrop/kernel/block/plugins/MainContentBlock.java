package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.Renderable;
import java.util.Map;
import java.util.Optional;

/** The content the page's controller produced, placed where the theme's blocks put it. */
@SpringDropPlugin(id = MainContentBlock.ID, type = BlockPlugin.class)
public class MainContentBlock implements BlockPlugin {

    public static final String ID = "system_main_block";

    @Override
    public String label() {
        return "Main page content";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return context.mainContent();
    }
}
