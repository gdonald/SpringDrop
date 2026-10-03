package dev.springdrop.kernel.field.formatter;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import java.util.Map;
import java.util.Optional;

/**
 * What a formatter needs to know about the field it is rendering: how it is
 * stored, how the bundle presents it, the settings the display gives the
 * formatter itself, and the entity the field belongs to when the caller has it.
 */
public record FormatterContext(
        FieldStorageConfig storage,
        FieldInstanceConfig instance,
        Map<String, Object> settings,
        Optional<EntityData> entity) {

    public FormatterContext {
        settings = Map.copyOf(settings);
    }

    public FormatterContext(FieldStorageConfig storage, FieldInstanceConfig instance, Map<String, Object> settings) {
        this(storage, instance, settings, Optional.empty());
    }

    /** The same field, rendered as part of this entity. */
    public FormatterContext withEntity(EntityData owner) {
        return new FormatterContext(storage, instance, settings, Optional.of(owner));
    }

    public String fieldName() {
        return storage.name();
    }

    public String label() {
        return instance.label();
    }

    /** The same field rendered with its label left off. */
    public FormatterContext withoutLabel() {
        FieldInstanceConfig unlabeled = new FieldInstanceConfig(instance.fieldName(), instance.entityTypeId(),
                instance.bundle(), "", instance.description(), instance.required(), instance.defaultValue(),
                instance.settings());
        return new FormatterContext(storage, unlabeled, settings, entity);
    }

    public Object setting(String key, Object fallback) {
        return settings.getOrDefault(key, fallback);
    }

    public String text(String key, String fallback) {
        return String.valueOf(setting(key, fallback));
    }
}
