package dev.springdrop.web;

import java.util.List;

/** One region of a section on the layout editor, with the blocks placed in it. */
public record LayoutEditorRegion(String id, String label, List<LayoutEditorRow> rows) {
}
