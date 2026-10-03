package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.MultipleValueWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One text input, named for the field, holding every entity a reference field
 * points at, separated by commas, each written as {@code Label (id)}. A name that matches nothing is
 * created on a free-tagging field, the way the single-value autocomplete does.
 * A name holding a comma or a double quote is written in double quotes, with
 * each double quote in it doubled.
 */
@SpringDropPlugin(id = EntityReferenceAutocompleteTagsWidget.ID, type = FieldWidget.class)
public class EntityReferenceAutocompleteTagsWidget implements MultipleValueWidget {

    public static final String ID = "entity_reference_autocomplete_tags";

    private final EntityReferenceAutocompleteWidget autocomplete;

    public EntityReferenceAutocompleteTagsWidget(EntityReferenceAutocompleteWidget autocomplete) {
        this.autocomplete = autocomplete;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement elementForAll(WidgetContext context, List<Object> values) {
        List<String> entries = values.stream().map(value -> quoted(autocomplete.label(context, value))).toList();
        FormElement input = FormElement.of(ElementType.TEXTFIELD, context.fieldName())
                .label(context.label())
                .description(joinedDescription(context.instance().description()))
                .value(String.join(", ", entries));
        if (context.required()) {
            input.markRequired();
        }
        return EntityReferenceAutocompleteWidget.withSuggestions(context, input);
    }

    @Override
    public List<Object> extractAll(WidgetContext context, Map<String, Object> submitted) {
        Object entered = submitted.get(context.fieldName());
        List<Object> values = new ArrayList<>();
        if (entered != null) {
            for (String entry : entries(entered.toString())) {
                values.add(autocomplete.resolve(context, entry));
            }
        }
        return values;
    }

    /** The entered text split at each comma outside double quotes, quotes removed and blanks left out. */
    static List<String> entries(String text) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"' && quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                current.append('"');
                index++;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                entries.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        entries.add(current.toString().trim());
        return entries.stream().filter(entry -> !entry.isEmpty()).toList();
    }

    private static String quoted(String entry) {
        return (entry.contains(",") || entry.contains("\""))
                ? "\"" + entry.replace("\"", "\"\"") + "\""
                : entry;
    }

    private static String joinedDescription(String description) {
        String separated = "Separate entries with commas.";
        return description.isBlank() ? separated : description + " " + separated;
    }
}
