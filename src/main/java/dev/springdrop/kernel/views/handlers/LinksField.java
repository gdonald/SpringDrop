package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/**
 * Buttons leading to pages about each result, such as its edit form. The
 * {@code links} setting lists them, each with a {@code label}, a {@code path}
 * where {@code {id}} stands for the result's id, and a {@code style},
 * {@code secondary} or {@code danger}. The view's access decides who sees them.
 */
@SpringDropPlugin(id = LinksField.ID, type = FieldHandler.class)
public class LinksField implements FieldHandler {

    public static final String ID = "entity_links";

    public static final String LINKS = "links";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Links";
    }

    @Override
    public boolean sortable() {
        return false;
    }

    @Override
    public String render(ResultRow row, HandlerConfig config) {
        StringBuilder markup = new StringBuilder();
        Object links = config.setting(LINKS, List.of());
        for (Object link : (links instanceof List<?> list) ? list : List.of()) {
            if (!(link instanceof Map<?, ?> each)) {
                continue;
            }
            String path = String.valueOf(each.get("path")).replace("{id}", String.valueOf(row.entity().id()));
            String style = "danger".equals(each.get("style")) ? "danger" : "secondary";
            if (markup.length() > 0) {
                markup.append(' ');
            }
            markup.append("<a class=\"btn btn-").append(style).append(" btn-sm\" href=\"")
                    .append(HtmlUtils.htmlEscape(path)).append("\">")
                    .append(HtmlUtils.htmlEscape(String.valueOf(each.get("label")))).append("</a>");
        }
        return markup.toString();
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, LABEL, "Label", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(LABEL, Settings.submitted(prefix, LABEL, submitted));
    }
}
