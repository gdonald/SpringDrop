package dev.springdrop.kernel.layout;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockManager;
import dev.springdrop.kernel.block.BlockRegionBuilder;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.TemplateSuggestions;
import dev.springdrop.kernel.theme.ThemeService;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Draws sections: each through the themed section template of its layout, with
 * every block it places built and drawn into the column of its region.
 *
 * <p>A section of a layout the site no longer has is left out, as is a block in
 * a region its layout does not have or of a plugin the site no longer has.
 * Widths the layout does not offer fall back to its first.
 */
@Component
public class SectionRenderer {

    public static final String TEMPLATE_DIRECTORY = "section";

    public static final String TEMPLATE_BASE = "section";

    static final int GRID_COLUMNS = 12;

    private static final int WHOLE_ROW_PERCENT = 100;

    private final LayoutManager layouts;
    private final BlockManager blocks;
    private final BlockRegionBuilder blockRenderer;
    private final ThemeService themes;

    public SectionRenderer(
            LayoutManager layouts, BlockManager blocks, BlockRegionBuilder blockRenderer, ThemeService themes) {
        this.layouts = layouts;
        this.blocks = blocks;
        this.blockRenderer = blockRenderer;
        this.themes = themes;
    }

    /** The sections in order, inside one container. */
    public Renderable render(List<Section> sections, BlockContext context) {
        Renderable container = Renderable.of("container").attribute("class", "layout-sections");
        for (Section section : sections) {
            Optional<Renderable> rendered = render(section, context);
            if (rendered.isPresent()) {
                container = container.child(rendered.get());
            }
        }
        return container;
    }

    public Optional<Renderable> render(Section section, BlockContext context) {
        if (!layouts.has(section.layout())) {
            return Optional.empty();
        }
        LayoutPlugin layout = layouts.plugin(section.layout());
        String widths = widths(layout, section.columnWidths());
        List<LayoutRegion> regions = layout.regions();
        List<Integer> percents = percents(widths, regions.size());

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("layout", section.layout());
        variables.put("columnWidths", widths);
        variables.put("columns", IntStream.range(0, regions.size())
                .mapToObj(index -> new Column(regions.get(index).id(), span(percents.get(index))))
                .toList());

        Renderable rendered = themes.build(
                TEMPLATE_DIRECTORY, TemplateSuggestions.of(TEMPLATE_BASE, section.layout()), variables);
        for (LayoutRegion region : regions) {
            for (SectionComponent component : section.inRegion(region.id())) {
                Optional<Renderable> block = block(component, context);
                if (block.isPresent()) {
                    rendered = rendered.child(block.get().inSlot(region.id()));
                }
            }
        }
        return Optional.of(rendered);
    }

    /** One region of a drawn section and how many of the grid's columns it spans from medium screens up. */
    public record Column(String region, int span) {
    }

    private Optional<Renderable> block(SectionComponent component, BlockContext context) {
        if (!blocks.has(component.block().plugin())) {
            return Optional.empty();
        }
        return blockRenderer.render(
                component.block(), BlockRegionBuilder.suggestions(component.block()), context);
    }

    private static String widths(LayoutPlugin layout, String chosen) {
        List<String> offered = layout.columnWidths();
        if (offered.contains(chosen)) {
            return chosen;
        }
        return offered.isEmpty() ? "" : offered.getFirst();
    }

    private static List<Integer> percents(String widths, int regionCount) {
        if (widths.isEmpty()) {
            return Collections.nCopies(regionCount, WHOLE_ROW_PERCENT);
        }
        return Arrays.stream(widths.split("-")).map(Integer::valueOf).toList();
    }

    static int span(int percent) {
        return Math.round(percent * GRID_COLUMNS / (float) WHOLE_ROW_PERCENT);
    }
}
