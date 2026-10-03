package dev.springdrop.kernel.views.rows;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.RowPlugin;
import dev.springdrop.kernel.views.Settings;
import dev.springdrop.kernel.views.ViewOptions;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.web.util.HtmlUtils;

/**
 * The result's fields, each in its own block, with its heading in front when
 * the {@code labels} setting is on. A field that draws nothing is left out.
 */
@SpringDropPlugin(id = FieldsRow.ID, type = RowPlugin.class)
public class FieldsRow implements RowPlugin {

    public static final String ID = "fields";

    public static final String LABELS = "labels";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Fields";
    }

    @Override
    public String render(ResultRow row, ViewOptions options, PluginConfig config,
            BiFunction<ResultRow, HandlerConfig, String> field, Function<HandlerConfig, String> heading) {
        boolean labels = Boolean.TRUE.equals(config.setting(LABELS, false)) || "true".equals(config.setting(LABELS, ""));
        StringBuilder markup = new StringBuilder();
        for (HandlerConfig each : options.fields() == null ? List.<HandlerConfig>of() : options.fields()) {
            String value = field.apply(row, each);
            if (value.isEmpty()) {
                continue;
            }
            markup.append("<div class=\"views-field views-field-").append(HtmlUtils.htmlEscape(each.id())).append("\">");
            if (labels) {
                markup.append("<span class=\"views-label fw-semibold\">").append(HtmlUtils.htmlEscape(heading.apply(each)))
                        .append(": </span>");
            }
            markup.append(value).append("</div>");
        }
        return markup.toString();
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.checkbox(prefix, LABELS, "Show each field's label", settings, false));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(LABELS, Settings.ticked(prefix, LABELS, submitted));
    }
}
