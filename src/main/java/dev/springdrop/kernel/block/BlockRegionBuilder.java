package dev.springdrop.kernel.block;

import dev.springdrop.kernel.block.plugins.MainContentBlock;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.Region;
import dev.springdrop.kernel.theme.TemplateSuggestions;
import dev.springdrop.kernel.theme.ThemeRegistry;
import dev.springdrop.kernel.theme.ThemeService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Builds the regions of a page from the blocks placed in the theme: each
 * placement whose conditions pass is built by its plugin and wrapped in the
 * themed block template, and the region holds them in weight order.
 *
 * <p>A placement in a region the theme does not have, or of a plugin the site no
 * longer has, is skipped. What the conditions depended on is carried whether the
 * block showed or not, since a different visitor may see a different answer.
 */
@Component
public class BlockRegionBuilder {

    public static final String TEMPLATE_DIRECTORY = "block";

    public static final String TEMPLATE_BASE = "block";

    private final BlockPlacementManager placements;
    private final BlockManager blocks;
    private final VisibilityConditionManager conditions;
    private final ThemeRegistry registry;
    private final ThemeService themes;

    public BlockRegionBuilder(
            BlockPlacementManager placements,
            BlockManager blocks,
            VisibilityConditionManager conditions,
            ThemeRegistry registry,
            ThemeService themes) {
        this.placements = placements;
        this.blocks = blocks;
        this.conditions = conditions;
        this.registry = registry;
        this.themes = themes;
    }

    public PageRegions build(String theme, BlockContext context) {
        List<String> regionIds = registry.regions(theme).stream().map(Region::id).toList();
        Map<String, List<Renderable>> byRegion = new LinkedHashMap<>();
        CacheMetadata cacheability = CacheMetadata.EMPTY.withTag(BlockPlacement.LIST_CACHE_TAG);
        boolean mainContentPlaced = false;

        for (BlockPlacement placement : placements.inTheme(theme)) {
            if (!regionIds.contains(placement.region()) || !blocks.has(placement.plugin())) {
                continue;
            }
            cacheability = cacheability.merge(conditions.cacheability(placement.visibility()));
            if (!conditions.passes(placement.visibility(), context)) {
                continue;
            }
            Optional<Renderable> block = render(placement, context);
            if (block.isPresent()) {
                byRegion.computeIfAbsent(placement.region(), region -> new ArrayList<>()).add(block.get());
                mainContentPlaced |= placement.plugin().equals(MainContentBlock.ID);
            }
        }

        Map<String, Renderable> regions = new LinkedHashMap<>();
        byRegion.forEach((region, rendered) -> regions.put(region, region(region, rendered)));
        return new PageRegions(regions, cacheability, mainContentPlaced);
    }

    /** One placement drawn through the block template, or nothing when its plugin has nothing to show. */
    public Optional<Renderable> render(BlockPlacement placement, BlockContext context) {
        BlockPlugin plugin = blocks.plugin(placement.plugin());
        return plugin.build(context, placement.settings()).map(content -> {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("id", placement.id());
            variables.put("plugin", placement.plugin());
            variables.put("label", placement.label());
            variables.put("labelDisplay", placement.labelDisplay());
            return themes.build(TEMPLATE_DIRECTORY, suggestions(placement), variables)
                    .child(content)
                    .cacheability(plugin.cacheability(placement.settings()))
                    .cacheTag(placement.cacheTag());
        });
    }

    /**
     * The placement's own template first, then the plugin with its derivative,
     * then the plugin, then the block template every block falls back to.
     */
    static List<String> suggestions(BlockPlacement placement) {
        List<String> names = new ArrayList<>();
        names.add(TEMPLATE_BASE + TemplateSuggestions.SEPARATOR + placement.id());
        names.addAll(TemplateSuggestions.of(TEMPLATE_BASE, placement.plugin().split(":")));
        return List.copyOf(names);
    }

    private static Renderable region(String region, List<Renderable> rendered) {
        Renderable container = Renderable.of("container").attribute("class", "region region-" + region);
        for (Renderable block : rendered) {
            container = container.child(block);
        }
        return container;
    }
}
