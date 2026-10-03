package dev.springdrop.kernel.views.styles;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import dev.springdrop.kernel.views.StyleContext;
import dev.springdrop.kernel.views.StylePlugin;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

/**
 * The results as a Bootstrap table, a column per field. A field the results
 * can be sorted by has a heading that sorts by it, when the {@code sortable}
 * setting is on.
 */
@SpringDropPlugin(id = TableStyle.ID, type = StylePlugin.class)
public class TableStyle implements StylePlugin {

    public static final String ID = "table";

    public static final String SORTABLE = "sortable";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Table";
    }

    @Override
    public String render(StyleContext context) {
        boolean sortable = !"false".equals(String.valueOf(context.style().setting(SORTABLE, true)));
        StringBuilder markup = new StringBuilder("<div class=\"table-responsive\"><table class=\"table align-middle "
                + "views-table\"><thead><tr>");
        for (HandlerConfig field : context.fields()) {
            String heading = HtmlUtils.htmlEscape(context.heading().apply(field));
            Optional<String> sort = sortable ? context.sortUrl().apply(field) : Optional.empty();
            markup.append("<th scope=\"col\">").append(sort.map(url -> "<a href=\"" + HtmlUtils.htmlEscape(url)
                    + "\">" + heading + "</a>").orElse(heading)).append("</th>");
        }
        markup.append("</tr></thead><tbody>");
        for (ResultRow row : context.rows()) {
            markup.append("<tr>");
            context.fields().forEach(field -> markup.append("<td>").append(context.field().apply(row, field))
                    .append("</td>"));
            markup.append("</tr>");
        }
        return markup.append("</tbody></table></div>").toString();
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.checkbox(prefix, SORTABLE, "Sort by a column's heading", settings, true));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(SORTABLE, Settings.ticked(prefix, SORTABLE, submitted));
    }
}
