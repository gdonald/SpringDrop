package dev.springdrop.kernel.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One row of a layout: the layout plugin it is built from, the column widths
 * it splits its regions by, and the blocks placed in each region. Blank widths
 * mean the layout's first.
 */
public record Section(String layout, String columnWidths, List<SectionComponent> components) {

    public Section {
        components = List.copyOf(components);
    }

    public static Section of(String layout) {
        return new Section(layout, "", List.of());
    }

    public Section withColumnWidths(String widths) {
        return new Section(layout, widths, components);
    }

    public Section withComponent(SectionComponent component) {
        List<SectionComponent> combined = new ArrayList<>(components);
        combined.add(component);
        return new Section(layout, columnWidths, combined);
    }

    public Section withComponents(List<SectionComponent> newComponents) {
        return new Section(layout, columnWidths, newComponents);
    }

    /** The same section with the block of this id replaced by the given one. */
    public Section replacing(SectionComponent replacement) {
        return withComponents(components.stream()
                .map(component -> component.block().id().equals(replacement.block().id()) ? replacement : component)
                .toList());
    }

    public Section withoutComponent(String blockId) {
        return withComponents(components.stream()
                .filter(component -> !component.block().id().equals(blockId))
                .toList());
    }

    public Optional<SectionComponent> component(String blockId) {
        return components.stream().filter(component -> component.block().id().equals(blockId)).findFirst();
    }

    /** The weight that puts a block after every other block in the region. */
    public int nextWeight(String region) {
        return inRegion(region).stream().mapToInt(SectionComponent::weight).max().orElse(-1) + 1;
    }

    /** The blocks placed in one region, lightest first. */
    public List<SectionComponent> inRegion(String region) {
        return components.stream()
                .filter(component -> component.region().equals(region))
                .sorted(Comparator.comparingInt(SectionComponent::weight))
                .toList();
    }
}
