package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.menu.BreadcrumbBuilder;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import java.util.Map;
import java.util.Optional;

/** The trail of links leading to the page being shown. */
@SpringDropPlugin(id = BreadcrumbBlock.ID, type = BlockPlugin.class)
public class BreadcrumbBlock implements BlockPlugin {

    public static final String ID = "system_breadcrumb_block";

    private final BreadcrumbBuilder breadcrumbs;

    public BreadcrumbBlock(BreadcrumbBuilder breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
    }

    @Override
    public String label() {
        return "Breadcrumbs";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return Optional.of(Renderable.of(BlockTemplates.ELEMENTS, "breadcrumb")
                .with("crumbs", breadcrumbs.build(context.path())));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY
                .withContext(BlockTemplates.URL_PATH_CONTEXT)
                .withContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }
}
