package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.formatter.FieldFormatterManager;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A configured field of the entity, drawn through a formatter: the one the
 * {@code formatter} setting names, or the field type's default, with the other
 * settings passed to it. An entity whose bundle does not have the field shows
 * nothing.
 */
@SpringDropPlugin(id = EntityFieldField.ID, type = FieldHandler.class)
public class EntityFieldField implements FieldHandler {

    public static final String ID = "entity_field";

    private final FieldConfigManager fields;
    private final FieldFormatterManager formatters;

    public EntityFieldField(FieldConfigManager fields, FieldFormatterManager formatters) {
        this.fields = fields;
        this.formatters = formatters;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Field";
    }

    @Override
    public String render(ResultRow row, HandlerConfig config) {
        return row.entity(config.relationship())
                .filter(entity -> fields.findInstance(entity.entityType(), entity.bundle(), config.property())
                        .isPresent())
                .map(entity -> {
                    Map<String, Object> settings = new LinkedHashMap<>(config.settings());
                    settings.remove(LABEL);
                    return formatters.render(formatters.context(entity.entityType(), entity.bundle(),
                            config.property(), settings).withoutLabel().withEntity(entity),
                            entity.fields().get(config.property()));
                })
                .orElse("");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, LABEL, "Label", settings),
                Settings.text(prefix, FieldFormatterManager.FORMATTER_SETTING, "Formatter", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LABEL, Settings.submitted(prefix, LABEL, submitted));
        String formatter = Settings.submitted(prefix, FieldFormatterManager.FORMATTER_SETTING, submitted);
        if (!formatter.isEmpty()) {
            values.put(FieldFormatterManager.FORMATTER_SETTING, formatter);
        }
        return values;
    }
}
