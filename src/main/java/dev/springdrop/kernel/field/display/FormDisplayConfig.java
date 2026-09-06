package dev.springdrop.kernel.field.display;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How one bundle's edit form is laid out in one form mode: which fields are
 * shown, in what order, with which widget, and which are disabled. Stored as a
 * config entity named {@code core.form_display.<entity type>.<bundle>.<mode>},
 * so a site's form layouts export with the rest of its configuration.
 */
public record FormDisplayConfig(
        String entityTypeId,
        String bundle,
        String mode,
        Map<String, FieldDisplaySlot> slots,
        List<String> disabled) {

    public static final String CONFIG_PREFIX = "core.form_display";

    public static final String DEFAULT_MODE = "default";

    public FormDisplayConfig {
        slots = Map.copyOf(slots);
        disabled = List.copyOf(disabled);
    }

    public static FormDisplayConfig of(String entityTypeId, String bundle, String mode) {
        return new FormDisplayConfig(entityTypeId, bundle, mode, Map.of(), List.of());
    }

    public static String configName(String entityTypeId, String bundle, String mode) {
        return CONFIG_PREFIX + "." + entityTypeId + "." + bundle + "." + mode;
    }

    /** The same display with one field placed, replacing any placement it had. */
    public FormDisplayConfig with(FieldDisplaySlot slot) {
        Map<String, FieldDisplaySlot> placed = new LinkedHashMap<>(slots);
        placed.put(slot.fieldName(), slot);
        List<String> stillDisabled = disabled.stream()
                .filter(fieldName -> !fieldName.equals(slot.fieldName()))
                .toList();
        return new FormDisplayConfig(entityTypeId, bundle, mode, placed, stillDisabled);
    }

    /** The same display with one field moved to the disabled region. */
    public FormDisplayConfig withoutField(String fieldName) {
        Map<String, FieldDisplaySlot> placed = new LinkedHashMap<>(slots);
        placed.remove(fieldName);
        List<String> stillDisabled = new java.util.ArrayList<>(disabled);
        if (!stillDisabled.contains(fieldName)) {
            stillDisabled.add(fieldName);
        }
        return new FormDisplayConfig(entityTypeId, bundle, mode, placed, stillDisabled);
    }
}
