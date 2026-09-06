package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.ValidReferenceConstraint;
import java.util.List;
import java.util.Map;

/**
 * A reference to another entity, stored as the target's id. The storage's
 * {@code target_type} setting names the entity type referred to, and the
 * instance's {@code target_bundles} setting narrows which bundles of it may be
 * chosen. A reference to something that is not there is rejected on save.
 */
@SpringDropPlugin(id = EntityReferenceFieldType.ID, type = FieldType.class)
public class EntityReferenceFieldType implements FieldType {

    public static final String ID = "entity_reference";

    public static final String TARGET_TYPE = "target_type";

    public static final String TARGET_BUNDLES = "target_bundles";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(FieldProperty.required(FieldProperty.VALUE, ColumnType.BIGINT));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", ValidReferenceConstraint.ID,
                Map.of("target", targetType(storage))));
    }

    public static String targetType(FieldStorageConfig storage) {
        Object target = storage.settings().get(TARGET_TYPE);
        return (target == null) ? "" : target.toString();
    }

    @Override
    public String defaultWidget() {
        return "entity_reference_autocomplete";
    }

    @Override
    public String defaultFormatter() {
        return "entity_reference_label";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of();
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of(TARGET_BUNDLES, List.of());
    }
}
