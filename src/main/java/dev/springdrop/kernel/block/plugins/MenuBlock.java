package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuNavigation;
import dev.springdrop.kernel.menu.MenuTreeBuilder;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.Link;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One menu drawn as a nested list, with the trail to the page being shown
 * marked. {@link MenuBlockDeriver} makes one per menu; the one for the main menu
 * is the primary menu block.
 */
public class MenuBlock implements BlockPlugin {

    private final MenuConfig menu;
    private final MenuNavigation navigation;

    public MenuBlock(MenuConfig menu, MenuNavigation navigation) {
        this.menu = menu;
        this.navigation = navigation;
    }

    @Override
    public String label() {
        return menu.label();
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        List<Link> links = navigation.of(menu.id(), context.path());
        if (links.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Renderable.of(BlockTemplates.ELEMENTS, "menu")
                .with("menuLabel", menu.label())
                .with("links", links));
    }

    /** The tree varies by permissions and by which page marks the trail. */
    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return MenuTreeBuilder.cacheability(menu.id()).withContext(BlockTemplates.URL_PATH_CONTEXT);
    }
}
