package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.FormattedTextConstraint;
import java.util.List;
import java.util.Map;

/** Formatted single-line text, bounded by the {@code max_length} storage setting, 255 by default. */
@SpringDropPlugin(id = TextFieldType.ID, type = FieldType.class)
public class TextFieldType implements FieldType {

    public static final String ID = "text";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(
                FieldProperty.required(FormattedText.VALUE, ColumnType.VARCHAR),
                FieldProperty.required(FormattedText.FORMAT, ColumnType.VARCHAR));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", FormattedTextConstraint.ID, Map.of(FormattedText.MAX_LENGTH, storage.settings().getOrDefault(FormattedText.MAX_LENGTH, 255))));
    }

    @Override
    public String defaultWidget() {
        return "text_textfield";
    }

    @Override
    public String defaultFormatter() {
        return "text_default";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of(FormattedText.MAX_LENGTH, 255);
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of();
    }
}
