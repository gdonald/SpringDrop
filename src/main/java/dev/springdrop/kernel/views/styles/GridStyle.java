package dev.springdrop.kernel.views.styles;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.Settings;
import dev.springdrop.kernel.views.StyleContext;
import dev.springdrop.kernel.views.StylePlugin;
import java.util.List;
import java.util.Map;

/** The results in a Bootstrap grid, the {@code columns} setting to a row from medium screens up. */
@SpringDropPlugin(id = GridStyle.ID, type = StylePlugin.class)
public class GridStyle implements StylePlugin {

    public static final String ID = "grid";

    public static final String COLUMNS = "columns";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Grid";
    }

    @Override
    public String render(StyleContext context) {
        int columns = Math.min(6, Math.max(1, context.style().number(COLUMNS, 4)));
        StringBuilder markup = new StringBuilder("<div class=\"row row-cols-1 row-cols-md-" + columns
                + " g-3 views-grid\">");
        context.rows().forEach(row -> markup.append("<div class=\"col views-row\">").append(context.row().apply(row))
                .append("</div>"));
        return markup.append("</div>").toString();
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.number(prefix, COLUMNS, "Columns", settings, 4));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(COLUMNS, Settings.whole(prefix, COLUMNS, submitted, 4));
    }
}
