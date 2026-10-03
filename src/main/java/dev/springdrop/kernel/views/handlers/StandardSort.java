package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.Settings;
import dev.springdrop.kernel.views.SortHandler;
import dev.springdrop.kernel.views.ViewExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Orders by the property, ascending unless the {@code order} setting is {@code desc}. */
@SpringDropPlugin(id = StandardSort.ID, type = SortHandler.class)
public class StandardSort implements SortHandler {

    public static final String ID = "standard";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Standard";
    }

    @Override
    public Sort sort(HandlerConfig config) {
        return config.text(ORDER).equals("desc") ? Sort.descending(config.property())
                : Sort.ascending(config.property());
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(
                Settings.select(prefix, ORDER, "Order", settings, List.of(new SelectOption("asc", "Ascending"),
                        new SelectOption("desc", "Descending"))),
                Settings.checkbox(prefix, ViewExecutor.EXPOSED, "Let the reader choose this sort", settings, false),
                Settings.text(prefix, "label", "Label", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(ORDER, Settings.chosen(prefix, ORDER, submitted, List.of("asc", "desc")));
        values.put(ViewExecutor.EXPOSED, Settings.ticked(prefix, ViewExecutor.EXPOSED, submitted));
        values.put("label", Settings.submitted(prefix, "label", submitted));
        return values;
    }
}
