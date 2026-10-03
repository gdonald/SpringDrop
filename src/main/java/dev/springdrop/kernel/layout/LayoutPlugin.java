package dev.springdrop.kernel.layout;

import java.util.List;

/**
 * A kind of layout a section is built from: its label, the regions blocks are
 * placed in, and the column widths it can split those regions by. A module
 * contributes one by annotating it with
 * {@code @SpringDropPlugin(type = LayoutPlugin.class)}.
 *
 * <p>Widths are written as each region's share of the row in percent, in
 * region order and joined by {@code -}, so {@code 33-67} gives the first region
 * a third. The first width listed is the one a section uses until it picks
 * another. A layout with one region lists none.
 */
public interface LayoutPlugin {

    String label();

    List<LayoutRegion> regions();

    default List<String> columnWidths() {
        return List.of();
    }
}
