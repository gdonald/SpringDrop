package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FilterHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compares a property with a value: {@code =}, {@code !=}, {@code <}, {@code >},
 * {@code contains}, or {@code in} a comma-separated list. The {@code type}
 * setting reads the value as {@code text}, a {@code number}, or a
 * {@code boolean}. A blank value adds nothing. A value that is not of the type
 * finds nothing, so an exposed filter given a word where a number goes lists
 * nothing rather than everything.
 */
@SpringDropPlugin(id = ValueFilter.ID, type = FilterHandler.class)
public class ValueFilter implements FilterHandler {

    public static final String ID = "value";

    public static final String OPERATOR = "operator";

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
        String given = value.strip();
        if (given.isEmpty()) {
            return Optional.empty();
        }
        String type = config.text(TypedValues.TYPE);
        String operator = String.valueOf(config.setting(OPERATOR, "="));
        if (operator.equals("in")) {
            List<Object> values = new ArrayList<>();
            for (String part : Arrays.stream(given.split(",")).map(String::strip).filter(each -> !each.isEmpty())
                    .toList()) {
                TypedValues.typed(type, part).ifPresent(values::add);
            }
            return Optional.of(Condition.in(config.property(), values));
        }
        Optional<Object> typed = TypedValues.typed(type, given);
        if (typed.isEmpty()) {
            return Optional.of(Condition.in(config.property(), List.of()));
        }
        return Optional.of(switch (operator) {
            case "!=" -> Condition.notEqual(config.property(), typed.get());
            case "<" -> Condition.lessThan(config.property(), typed.get());
            case ">" -> Condition.greaterThan(config.property(), typed.get());
            case "contains" -> Condition.contains(config.property(), given);
            default -> Condition.equal(config.property(), typed.get());
        });
    }

    @Override
    public FormElement exposedElement(HandlerConfig config, String current) {
        String label = config.text("label").isEmpty() ? config.property() : config.text("label");
        if (config.text(TypedValues.TYPE).equals(TypedValues.BOOLEAN)) {
            return FormElement.of(ElementType.SELECT, FilterHandler.identifier(config)).label(label).value(current)
                    .options(List.of(new SelectOption("", "Any"),
                            new SelectOption("true", String.valueOf(config.setting("true_label", "Yes"))),
                            new SelectOption("false", String.valueOf(config.setting("false_label", "No")))));
        }
        return FormElement.of(ElementType.TEXTFIELD, FilterHandler.identifier(config)).label(label).value(current);
    }

    static final List<String> OPERATORS = List.of("=", "!=", "<", ">", "contains", "in");

    static final List<String> TYPES = List.of(TypedValues.TEXT, TypedValues.NUMBER, TypedValues.BOOLEAN);

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(
                Settings.select(prefix, OPERATOR, "Operator", settings, OPERATORS.stream()
                        .map(operator -> new SelectOption(operator, operator)).toList()),
                Settings.text(prefix, VALUE, "Value", settings),
                Settings.select(prefix, TypedValues.TYPE, "Kind of value", settings, TYPES.stream()
                        .map(type -> new SelectOption(type, type)).toList()),
                Settings.checkbox(prefix, EXPOSED, "Let the reader give the value", settings, false),
                Settings.text(prefix, IDENTIFIER, "Name the value is given under", settings),
                Settings.text(prefix, "label", "Label", settings),
                Settings.text(prefix, "true_label", "Shown for true", settings),
                Settings.text(prefix, "false_label", "Shown for false", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(OPERATOR, Settings.chosen(prefix, OPERATOR, submitted, OPERATORS));
        values.put(VALUE, Settings.submitted(prefix, VALUE, submitted));
        values.put(TypedValues.TYPE, Settings.chosen(prefix, TypedValues.TYPE, submitted, TYPES));
        values.put(EXPOSED, Settings.ticked(prefix, EXPOSED, submitted));
        values.put(IDENTIFIER, Settings.submitted(prefix, IDENTIFIER, submitted));
        values.put("label", Settings.submitted(prefix, "label", submitted));
        values.put("true_label", Settings.submitted(prefix, "true_label", submitted));
        values.put("false_label", Settings.submitted(prefix, "false_label", submitted));
        return values;
    }
}
