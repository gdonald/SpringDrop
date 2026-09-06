package dev.springdrop.kernel.field.display;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Builds the fields of an edit form the way the bundle's form display says: in
 * weight order, each through the widget the display names, leaving out the ones
 * moved to the disabled region. A bundle with no display saved yet shows every
 * field it has, in the order the fields were added.
 */
@Component
public class FormDisplayManager {

    private final ConfigStore configStore;
    private final FieldConfigManager fieldConfigManager;
    private final FieldWidgetManager widgets;

    public FormDisplayManager(
            ConfigStore configStore, FieldConfigManager fieldConfigManager, FieldWidgetManager widgets) {
        this.configStore = configStore;
        this.fieldConfigManager = fieldConfigManager;
        this.widgets = widgets;
    }

    public void save(FormDisplayConfig display) {
        configStore.save(
                FormDisplayConfig.configName(display.entityTypeId(), display.bundle(), display.mode()), display);
    }

    public Optional<FormDisplayConfig> find(String entityTypeId, String bundle, String mode) {
        return Optional.ofNullable(configStore.read(
                FormDisplayConfig.configName(entityTypeId, bundle, mode), FormDisplayConfig.class, null));
    }

    public void delete(String entityTypeId, String bundle, String mode) {
        configStore.delete(FormDisplayConfig.configName(entityTypeId, bundle, mode));
    }

    /** The fields of the edit form, in the order the display puts them. */
    public List<FormElement> build(
            String entityTypeId, String bundle, String mode, Map<String, Object> values) {

        Optional<FormDisplayConfig> display = find(entityTypeId, bundle, mode);
        List<FormElement> elements = new ArrayList<>();
        for (String fieldName : shownFields(entityTypeId, bundle, display)) {
            WidgetContext context = widgets.context(entityTypeId, bundle, fieldName);
            elements.add(widgets.build(withWidget(context, display), valuesOf(values, fieldName), 0));
        }
        return List.copyOf(elements);
    }

    /** Everything a form of this bundle holds, wrapped so it can be rendered as one. */
    public FormElement buildContainer(
            String entityTypeId, String bundle, String mode, Map<String, Object> values) {

        FormElement container = FormElement.of(ElementType.CONTAINER, bundle + "-fields");
        build(entityTypeId, bundle, mode, values).forEach(container::child);
        return container;
    }

    private List<String> shownFields(
            String entityTypeId, String bundle, Optional<FormDisplayConfig> display) {

        List<String> fields = fieldConfigManager.fieldNames(entityTypeId, bundle);
        if (display.isEmpty()) {
            return fields;
        }

        FormDisplayConfig layout = display.get();
        return fields.stream()
                .filter(fieldName -> !layout.disabled().contains(fieldName))
                .sorted(Comparator.comparingInt(fieldName -> weightOf(layout, fieldName)))
                .toList();
    }

    /** The widget the display names for this field, or the one the field already had. */
    private static WidgetContext withWidget(WidgetContext context, Optional<FormDisplayConfig> display) {
        return slotOf(context, display)
                .map(slot -> new WidgetContext(context.storage(), context.instance()
                        .withSettings(withWidgetSetting(context, slot))))
                .orElse(context);
    }

    private static Map<String, Object> withWidgetSetting(WidgetContext context, FieldDisplaySlot slot) {
        Map<String, Object> settings = new java.util.LinkedHashMap<>(context.instance().settings());
        settings.putAll(slot.settings());
        settings.put(FieldWidgetManager.WIDGET_SETTING, slot.handler());
        return settings;
    }

    private static Optional<FieldDisplaySlot> slotOf(WidgetContext context, Optional<FormDisplayConfig> display) {
        return display.map(layout -> layout.slots().get(context.fieldName()));
    }

    private static int weightOf(FormDisplayConfig display, String fieldName) {
        FieldDisplaySlot slot = display.slots().get(fieldName);
        return (slot == null) ? 0 : slot.weight();
    }

    private static List<Object> valuesOf(Map<String, Object> values, String fieldName) {
        Object value = values.get(fieldName);
        if (value == null) {
            return List.of();
        }
        return (value instanceof List<?> list) ? List.copyOf(list) : List.of(value);
    }
}
