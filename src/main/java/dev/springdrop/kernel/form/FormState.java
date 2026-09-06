package dev.springdrop.kernel.form;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a form carries between one request and the next: the submitted values,
 * the errors found in them, the element that triggered the submission, storage
 * a multi-step form keeps its work in, and whether the form should be rebuilt
 * rather than finished.
 */
public class FormState {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final Map<String, Object> storage = new LinkedHashMap<>();
    private String triggeringElement = "";
    private boolean rebuild;

    public static FormState of(Map<String, Object> submitted) {
        FormState state = new FormState();
        state.values.putAll(submitted);
        return state;
    }

    public Map<String, Object> values() {
        return Map.copyOf(values);
    }

    public Optional<Object> value(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public FormState value(String name, Object value) {
        values.put(name, value);
        return this;
    }

    /** Records an error against the element it belongs to. */
    public FormState error(String elementName, String message) {
        errors.put(elementName, message);
        return this;
    }

    public Map<String, String> errors() {
        return Map.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** Values a multi-step form keeps across steps, never rendered as controls. */
    public Map<String, Object> storage() {
        return Map.copyOf(storage);
    }

    public FormState store(String key, Object value) {
        storage.put(key, value);
        return this;
    }

    public String triggeringElement() {
        return triggeringElement;
    }

    public FormState triggeredBy(String elementName) {
        this.triggeringElement = elementName;
        return this;
    }

    public boolean rebuild() {
        return rebuild;
    }

    /** Asks for the form to be built again instead of finished, as a step does. */
    public FormState rebuild(boolean shouldRebuild) {
        this.rebuild = shouldRebuild;
        return this;
    }
}
