package dev.springdrop.web;

import dev.springdrop.kernel.block.PageHelp;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The help text core gives for its own administration pages. */
@Component
public class CoreHelp implements PageHelp {

    public static final String BLOCK_LAYOUT = "Blocks are placed in the regions of a theme. "
            + "Within a region, lighter blocks come first, and each block shows only where its "
            + "visibility conditions allow.";

    public static final String CUSTOM_BLOCKS = "A custom block is written once here and placed from "
            + "the block layout, as many times as it is wanted.";

    @Override
    public Optional<String> helpFor(String path) {
        if (path.startsWith(BlockLayoutController.PATH)) {
            return Optional.of(BLOCK_LAYOUT);
        }
        if (path.startsWith(CustomBlockController.PATH)) {
            return Optional.of(CUSTOM_BLOCKS);
        }
        return Optional.empty();
    }
}
