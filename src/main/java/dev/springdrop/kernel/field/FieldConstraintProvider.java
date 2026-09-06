package dev.springdrop.kernel.field;

import dev.springdrop.kernel.entity.EntityConstraintProvider;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.CardinalityConstraint;
import dev.springdrop.kernel.validation.constraints.NotNullConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Turns field configuration into constraints on save: a required instance must
 * be filled in, and a field may not hold more values than its storage allows.
 */
@Component
public class FieldConstraintProvider implements EntityConstraintProvider {

    private static final String FIELD_PATH_PREFIX = "fields.";

    private final FieldConfigManager fieldConfigManager;

    public FieldConstraintProvider(FieldConfigManager fieldConfigManager) {
        this.fieldConfigManager = fieldConfigManager;
    }

    @Override
    public List<ConstraintSpec> constraintsFor(EntityType type, String bundle) {
        if (bundle == null) {
            return List.of();
        }

        List<ConstraintSpec> specs = new ArrayList<>();
        for (FieldInstanceConfig instance : fieldConfigManager.instances(type.id(), bundle)) {
            String path = FIELD_PATH_PREFIX + instance.fieldName();
            if (instance.required()) {
                specs.add(ConstraintSpec.on(path, NotNullConstraint.ID));
            }
            fieldConfigManager.findStorage(type.id(), instance.fieldName()).ifPresent(storage -> {
                if (!storage.unlimited()) {
                    specs.add(ConstraintSpec.on(
                            path, CardinalityConstraint.ID, Map.of("max", storage.cardinality())));
                }
                specs.addAll(typeConstraints(path, storage, instance));
            });
        }
        return List.copyOf(specs);
    }

    /** The field type's own constraints, bound to the field being validated. */
    private List<ConstraintSpec> typeConstraints(
            String path, FieldStorageConfig storage, FieldInstanceConfig instance) {

        return fieldConfigManager.fieldType(storage.type())
                .defaultConstraints(storage, instance).stream()
                .map(spec -> spec.at(path).forEachItem())
                .toList();
    }
}
