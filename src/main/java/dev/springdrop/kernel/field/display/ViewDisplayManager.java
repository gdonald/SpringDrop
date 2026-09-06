package dev.springdrop.kernel.field.display;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.formatter.FieldFormatterManager;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Renders a bundle for reading the way its view display says: in weight order,
 * each field through the formatter the display names, with labels left off where
 * the display asks, and the fields it disables left out. A bundle with no
 * display saved shows every field it has.
 */
@Component
public class ViewDisplayManager {

    private final ConfigStore configStore;
    private final FieldConfigManager fieldConfigManager;
    private final FieldFormatterManager formatters;

    public ViewDisplayManager(
            ConfigStore configStore,
            FieldConfigManager fieldConfigManager,
            FieldFormatterManager formatters) {
        this.configStore = configStore;
        this.fieldConfigManager = fieldConfigManager;
        this.formatters = formatters;
    }

    public void save(ViewDisplayConfig display) {
        configStore.save(
                ViewDisplayConfig.configName(display.entityTypeId(), display.bundle(), display.mode()), display);
    }

    public Optional<ViewDisplayConfig> find(String entityTypeId, String bundle, String mode) {
        return Optional.ofNullable(configStore.read(
                ViewDisplayConfig.configName(entityTypeId, bundle, mode), ViewDisplayConfig.class, null));
    }

    public void delete(String entityTypeId, String bundle, String mode) {
        configStore.delete(ViewDisplayConfig.configName(entityTypeId, bundle, mode));
    }

    /** The bundle's fields rendered for reading, in the order the display puts them. */
    public String render(String entityTypeId, String bundle, String mode, Map<String, Object> values) {
        Optional<ViewDisplayConfig> display = find(entityTypeId, bundle, mode);

        StringBuilder markup = new StringBuilder();
        for (String fieldName : shownFields(entityTypeId, bundle, display)) {
            markup.append(formatters.render(context(entityTypeId, bundle, fieldName, display),
                    values.get(fieldName)));
        }
        return markup.toString();
    }

    private FormatterContext context(
            String entityTypeId, String bundle, String fieldName, Optional<ViewDisplayConfig> display) {

        FormatterContext context = formatters.context(
                entityTypeId, bundle, fieldName, settingsOf(display, fieldName));
        return showsLabel(display, fieldName) ? context : withoutLabel(context);
    }

    private static FormatterContext withoutLabel(FormatterContext context) {
        FieldInstanceConfig unlabelled = new FieldInstanceConfig(
                context.instance().fieldName(),
                context.instance().entityTypeId(),
                context.instance().bundle(),
                "",
                context.instance().description(),
                context.instance().required(),
                context.instance().defaultValue(),
                context.instance().settings());
        return new FormatterContext(context.storage(), unlabelled, context.settings());
    }

    private static Map<String, Object> settingsOf(Optional<ViewDisplayConfig> display, String fieldName) {
        FieldDisplaySlot slot = display.map(layout -> layout.slots().get(fieldName)).orElse(null);
        if (slot == null) {
            return Map.of();
        }
        Map<String, Object> settings = new LinkedHashMap<>(slot.settings());
        settings.put(FieldFormatterManager.FORMATTER_SETTING, slot.handler());
        return settings;
    }

    private static boolean showsLabel(Optional<ViewDisplayConfig> display, String fieldName) {
        return display.map(layout -> layout.showsLabelOf(fieldName)).orElse(true);
    }

    private List<String> shownFields(
            String entityTypeId, String bundle, Optional<ViewDisplayConfig> display) {

        List<String> fields = fieldConfigManager.fieldNames(entityTypeId, bundle);
        if (display.isEmpty()) {
            return fields;
        }

        ViewDisplayConfig layout = display.get();
        return fields.stream()
                .filter(fieldName -> !layout.disabled().contains(fieldName))
                .sorted(Comparator.comparingInt(fieldName -> weightOf(layout, fieldName)))
                .toList();
    }

    private static int weightOf(ViewDisplayConfig display, String fieldName) {
        FieldDisplaySlot slot = display.slots().get(fieldName);
        return (slot == null) ? 0 : slot.weight();
    }
}
