package dev.springdrop.kernel.block;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.render.Renderable;
import java.util.Optional;

/**
 * The page a block is built for: its path, its title, the main content a
 * controller produced for it, and the entity the route is about, when it is
 * about one. Blocks read what they need from it, and visibility conditions
 * decide from it whether a block shows at all.
 */
public record BlockContext(
        String path, String title, Optional<Renderable> mainContent, Optional<EntityData> routeEntity) {

    public static BlockContext of(String path, String title) {
        return new BlockContext(path, title, Optional.empty(), Optional.empty());
    }

    public BlockContext withMainContent(Renderable content) {
        return new BlockContext(path, title, Optional.of(content), routeEntity);
    }

    public BlockContext withRouteEntity(EntityData entity) {
        return new BlockContext(path, title, mainContent, Optional.of(entity));
    }
}
