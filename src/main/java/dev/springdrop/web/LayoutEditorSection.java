package dev.springdrop.web;

import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.layout.LayoutRegion;
import java.util.List;

/**
 * One section on the layout editor: where it sits, the layout it is built from,
 * the widths it may choose between and the one it has, and its regions.
 */
public record LayoutEditorSection(
        int index,
        String layoutLabel,
        List<SelectOption> widthOptions,
        String widths,
        List<LayoutRegion> regionOptions,
        List<LayoutEditorRegion> regions) {
}
