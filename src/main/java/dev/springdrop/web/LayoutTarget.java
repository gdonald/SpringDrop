package dev.springdrop.web;

import dev.springdrop.kernel.layout.Section;
import java.util.List;
import java.util.function.Consumer;

/**
 * A layout the editor works on: a bundle's default for a view mode, or one
 * entity's own. It names the bundle whose blocks are on offer, the path its
 * editor lives at, the sections it holds, and how changed sections are saved.
 */
record LayoutTarget(
        String entityTypeId, String bundle, String path, List<Section> sections, Consumer<List<Section>> saver) {

    LayoutTarget {
        sections = List.copyOf(sections);
    }

    void save(List<Section> changed) {
        saver.accept(changed);
    }
}
