package dev.springdrop.kernel.field;

import dev.springdrop.kernel.validation.ConstraintSpec;
import java.util.List;
import java.util.Map;

/**
 * A kind of field value: what it stores, what it must satisfy, and which widget
 * and formatter handle it unless a display says otherwise. A field type is a
 * plugin registered with {@code @SpringDropPlugin(type = FieldType.class)}, so a
 * module adds one without core knowing about it.
 */
public interface FieldType {

    String id();

    /** The properties one value of this type holds. */
    List<FieldProperty> properties();

    /**
     * The constraints every field of this type carries, given its storage and the
     * instance it is attached to. Their property paths are left empty; the field
     * system binds them to the field being validated.
     */
    List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance);

    String defaultWidget();

    String defaultFormatter();

    Map<String, Object> defaultStorageSettings();

    Map<String, Object> defaultInstanceSettings();
}
