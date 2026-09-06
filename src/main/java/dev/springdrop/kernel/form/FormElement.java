package dev.springdrop.kernel.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One node of a form: what kind of control it is, what it is called, and
 * everything the renderer needs to draw it. Containers carry children, so a form
 * is a tree built up a call at a time. An element the current user may not see is
 * marked without access and is left out of the rendered form entirely.
 */
public final class FormElement {

    private final String name;
    private final ElementType type;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<ElementState> states = new ArrayList<>();
    private final List<ValidationRule> rules = new ArrayList<>();
    private final List<SelectOption> options = new ArrayList<>();
    private final List<FormElement> children = new ArrayList<>();
    private String label = "";
    private String description = "";
    private boolean required;
    private boolean access = true;
    private Object value;

    private FormElement(ElementType type, String name) {
        this.type = type;
        this.name = name;
    }

    public static FormElement of(ElementType type, String name) {
        return new FormElement(type, name);
    }

    public FormElement label(String newLabel) {
        this.label = newLabel;
        return this;
    }

    /** Help text shown under the control. */
    public FormElement description(String newDescription) {
        this.description = newDescription;
        return this;
    }

    public FormElement markRequired() {
        this.required = true;
        return this;
    }

    public FormElement value(Object newValue) {
        this.value = newValue;
        return this;
    }

    public FormElement attribute(String attributeName, String attributeValue) {
        attributes.put(attributeName, attributeValue);
        return this;
    }

    /** Shown only while another element holds the given value. */
    public FormElement visibleWhen(String dependsOn, String equalsValue) {
        return state(ElementState.VISIBLE, dependsOn, equalsValue);
    }

    /** Required only while another element holds the given value. */
    public FormElement requiredWhen(String dependsOn, String equalsValue) {
        return state(ElementState.REQUIRED, dependsOn, equalsValue);
    }

    /** Disabled while another element holds the given value. */
    public FormElement disabledWhen(String dependsOn, String equalsValue) {
        return state(ElementState.DISABLED, dependsOn, equalsValue);
    }

    private FormElement state(String condition, String dependsOn, String equalsValue) {
        states.add(new ElementState(condition, dependsOn, equalsValue));
        return this;
    }

    /** A rule the value must satisfy, checked on both sides. */
    public FormElement rule(ValidationRule newRule) {
        rules.add(newRule);
        return this;
    }

    public FormElement options(List<SelectOption> newOptions) {
        options.clear();
        options.addAll(newOptions);
        return this;
    }

    public FormElement child(FormElement element) {
        children.add(element);
        return this;
    }

    /** Marks the element as one the current user may not see. */
    public FormElement withoutAccess() {
        this.access = false;
        return this;
    }

    public String name() {
        return name;
    }

    public ElementType type() {
        return type;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean required() {
        return required;
    }

    public boolean access() {
        return access;
    }

    public Object value() {
        return value;
    }

    public Map<String, String> attributes() {
        return Map.copyOf(attributes);
    }

    public List<ElementState> states() {
        return List.copyOf(states);
    }

    public List<ValidationRule> rules() {
        return List.copyOf(rules);
    }

    public List<SelectOption> options() {
        return List.copyOf(options);
    }

    public List<FormElement> children() {
        return List.copyOf(children);
    }
}
