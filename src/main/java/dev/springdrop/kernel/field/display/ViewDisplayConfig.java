package dev.springdrop.kernel.field.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How one bundle is rendered for reading in one view mode: which fields are
 * shown, in what order, through which formatter, whether each label is shown,
 * and which fields are left out. Stored as a config entity named
 * {@code core.view_display.<entity type>.<bundle>.<mode>}.
 */
public record ViewDisplayConfig(
        String entityTypeId,
        String bundle,
        String mode,
        Map<String, FieldDisplaySlot> slots,
        List<String> hiddenLabels,
        List<String> disabled) {

    public static final String CONFIG_PREFIX = "core.view_display";

    public static final String DEFAULT_MODE = "default";

    public static final String TEASER_MODE = "teaser";

    public static final String FULL_MODE = "full";

    public ViewDisplayConfig {
        slots = Map.copyOf(slots);
        hiddenLabels = List.copyOf(hiddenLabels);
        disabled = List.copyOf(disabled);
    }

    public static ViewDisplayConfig of(String entityTypeId, String bundle, String mode) {
        return new ViewDisplayConfig(entityTypeId, bundle, mode, Map.of(), List.of(), List.of());
    }

    public static String configName(String entityTypeId, String bundle, String mode) {
        return CONFIG_PREFIX + "." + entityTypeId + "." + bundle + "." + mode;
    }

    /** The same display with one field placed, replacing any placement it had. */
    public ViewDisplayConfig with(FieldDisplaySlot slot) {
        Map<String, FieldDisplaySlot> placed = new LinkedHashMap<>(slots);
        placed.put(slot.fieldName(), slot);
        List<String> stillDisabled = disabled.stream()
                .filter(fieldName -> !fieldName.equals(slot.fieldName()))
                .toList();
        return new ViewDisplayConfig(entityTypeId, bundle, mode, placed, hiddenLabels, stillDisabled);
    }

    /** The same display with one field's label left off. */
    public ViewDisplayConfig withoutLabel(String fieldName) {
        List<String> hidden = new ArrayList<>(hiddenLabels);
        if (!hidden.contains(fieldName)) {
            hidden.add(fieldName);
        }
        return new ViewDisplayConfig(entityTypeId, bundle, mode, slots, hidden, disabled);
    }

    /** The same display with one field left out of this view mode. */
    public ViewDisplayConfig withoutField(String fieldName) {
        Map<String, FieldDisplaySlot> placed = new LinkedHashMap<>(slots);
        placed.remove(fieldName);
        List<String> stillDisabled = new ArrayList<>(disabled);
        if (!stillDisabled.contains(fieldName)) {
            stillDisabled.add(fieldName);
        }
        return new ViewDisplayConfig(entityTypeId, bundle, mode, placed, hiddenLabels, stillDisabled);
    }

    public boolean showsLabelOf(String fieldName) {
        return !hiddenLabels.contains(fieldName);
    }
}
