package dev.springdrop.kernel.field.widget;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;

/**
 * What a widget needs to know about the field it is editing: how it is stored,
 * and how the bundle it is attached to presents it.
 */
public record WidgetContext(FieldStorageConfig storage, FieldInstanceConfig instance) {

    public String fieldName() {
        return storage.name();
    }

    public String label() {
        return instance.label();
    }

    public boolean required() {
        return instance.required();
    }

    public int cardinality() {
        return storage.cardinality();
    }

    public boolean unlimited() {
        return storage.unlimited();
    }

    /** The name the value at one delta is submitted under. */
    public String elementName(int delta) {
        return single() ? fieldName() : fieldName() + "[" + delta + "]";
    }

    /** A field holding one value needs no delta in its name. */
    public boolean single() {
        return storage.cardinality() == 1;
    }
}
