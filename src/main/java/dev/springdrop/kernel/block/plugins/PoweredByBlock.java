package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.Renderable;
import java.util.Map;
import java.util.Optional;

/** A line saying the site runs on SpringDrop. */
@SpringDropPlugin(id = PoweredByBlock.ID, type = BlockPlugin.class)
public class PoweredByBlock implements BlockPlugin {

    public static final String ID = "system_powered_by_block";

    @Override
    public String label() {
        return "Powered by SpringDrop";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return Optional.of(Renderable.of(BlockTemplates.ELEMENTS, "poweredBy"));
    }
}
