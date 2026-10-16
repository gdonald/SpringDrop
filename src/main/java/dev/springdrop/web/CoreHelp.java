package dev.springdrop.web;

import dev.springdrop.kernel.block.PageHelp;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The help text core gives for its own administration pages: a page's own help
 * first, and otherwise the help of the section it is in.
 */
@Component
public class CoreHelp implements PageHelp {

    public static final String BLOCK_LAYOUT = "Blocks are placed in the regions of a theme. "
            + "Within a region, lighter blocks come first, and each block shows only where its "
            + "visibility conditions allow.";

    public static final String CUSTOM_BLOCKS = "A custom block is written once here and placed from "
            + "the block layout, as many times as it is wanted.";

    /** Each section's help, by the path the section's pages start with. */
    static final Map<String, String> SECTIONS = Map.of(
            ModulesController.PATH, "Modules add features to the site. Enabling a module also enables the modules "
                    + "it needs, and a module another enabled module needs stays on.",
            "/admin/content", "Content lists what is published and drafted on the site, with its comments, media, "
                    + "and custom blocks.",
            AdminSectionController.STRUCTURE_PATH, "Structure holds what the site's content is built from: content "
                    + "types, taxonomy, menus, blocks, and views.",
            AdminSectionController.CONFIGURATION_PATH, "Configuration holds the settings of the site and the "
                    + "services it offers.",
            PeopleController.PATH, "People lists the site's accounts, and Permissions sets what each role may do.",
            AdminSectionController.REPORTS_PATH, "Reports show what the site has logged and whether it has what "
                    + "it needs to run.");

    @Override
    public Optional<String> helpFor(String path) {
        if (path.startsWith(BlockLayoutController.PATH)) {
            return Optional.of(BLOCK_LAYOUT);
        }
        if (path.startsWith(CustomBlockController.PATH)) {
            return Optional.of(CUSTOM_BLOCKS);
        }
        return SECTIONS.entrySet().stream().filter(section -> path.equals(section.getKey())
                || path.startsWith(section.getKey() + "/")).map(Map.Entry::getValue).findFirst();
    }
}
