package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.ArgumentHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.Settings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Finds results whose property equals the value, read as the {@code type} setting says. */
@SpringDropPlugin(id = ValueArgument.ID, type = ArgumentHandler.class)
public class ValueArgument implements ArgumentHandler {

    public static final String ID = "value";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Value";
    }

    @Override
    public Optional<Condition> condition(HandlerConfig config, String value) {
        return TypedValues.typed(config.text(TypedValues.TYPE), value)
                .map(typed -> Condition.equal(config.property(), typed));
    }

    static final List<String> ACTIONS = List.of(IGNORE, EMPTY, FIXED);

    static List<FormElement> defaultActionForm(String prefix, Map<String, Object> settings) {
        return List.of(
                Settings.select(prefix, DEFAULT_ACTION, "Without a value", settings, List.of(
                        new SelectOption(IGNORE, "Show every result"), new SelectOption(EMPTY, "Show nothing"),
                        new SelectOption(FIXED, "Use the fixed value"))),
                Settings.text(prefix, DEFAULT_VALUE, "Fixed value", settings));
    }

    static void putDefaultAction(String prefix, Map<String, String> submitted, Map<String, Object> values) {
        values.put(DEFAULT_ACTION, Settings.chosen(prefix, DEFAULT_ACTION, submitted, ACTIONS));
        values.put(DEFAULT_VALUE, Settings.submitted(prefix, DEFAULT_VALUE, submitted));
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        List<FormElement> elements = new ArrayList<>(List.of(Settings.select(prefix, TypedValues.TYPE,
                "Kind of value", settings, ValueFilter.TYPES.stream().map(type -> new SelectOption(type, type))
                        .toList())));
        elements.addAll(defaultActionForm(prefix, settings));
        return elements;
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(TypedValues.TYPE, Settings.chosen(prefix, TypedValues.TYPE, submitted, ValueFilter.TYPES));
        putDefaultAction(prefix, submitted, values);
        return values;
    }
}
