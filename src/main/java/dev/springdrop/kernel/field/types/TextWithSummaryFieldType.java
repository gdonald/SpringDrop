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

/** Formatted text of any length with a summary beside it, shown where the full text is too long. */
@SpringDropPlugin(id = TextWithSummaryFieldType.ID, type = FieldType.class)
public class TextWithSummaryFieldType implements FieldType {

    public static final String ID = "text_with_summary";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(
                FieldProperty.required(FormattedText.VALUE, ColumnType.TEXT),
                new FieldProperty(FormattedText.SUMMARY, ColumnType.TEXT, false),
                FieldProperty.required(FormattedText.FORMAT, ColumnType.VARCHAR));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", FormattedTextConstraint.ID, Map.of(FormattedText.REQUIRED_SUMMARY, Boolean.TRUE.equals(instance.settings().get(FormattedText.REQUIRED_SUMMARY)))));
    }

    @Override
    public String defaultWidget() {
        return "text_textarea_with_summary";
    }

    @Override
    public String defaultFormatter() {
        return "text_default";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of();
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of(FormattedText.DISPLAY_SUMMARY, true, FormattedText.REQUIRED_SUMMARY, false);
    }
}
