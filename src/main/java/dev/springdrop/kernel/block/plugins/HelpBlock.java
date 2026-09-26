package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.block.PageHelp;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The help text a module gives for the page being shown, when it gives any. */
@SpringDropPlugin(id = HelpBlock.ID, type = BlockPlugin.class)
public class HelpBlock implements BlockPlugin {

    public static final String ID = "help_block";

    private final List<PageHelp> help;

    public HelpBlock(List<PageHelp> help) {
        this.help = help;
    }

    @Override
    public String label() {
        return "Help";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return help.stream()
                .map(provider -> provider.helpFor(context.path()))
                .flatMap(Optional::stream)
                .findFirst()
                .map(text -> Renderable.of(BlockTemplates.ELEMENTS, "help").with("text", text));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY.withContext(BlockTemplates.URL_PATH_CONTEXT);
    }
}
