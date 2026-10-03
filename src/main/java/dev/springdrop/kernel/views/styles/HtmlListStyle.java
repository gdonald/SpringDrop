package dev.springdrop.kernel.views.styles;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.Settings;
import dev.springdrop.kernel.views.StyleContext;
import dev.springdrop.kernel.views.StylePlugin;
import java.util.List;
import java.util.Map;

/** The results as a list, bulleted unless the {@code type} setting is {@code ol}. */
@SpringDropPlugin(id = HtmlListStyle.ID, type = StylePlugin.class)
public class HtmlListStyle implements StylePlugin {

    public static final String ID = "html_list";

    public static final String TYPE = "type";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "HTML list";
    }

    @Override
    public String render(StyleContext context) {
        String tag = "ol".equals(context.style().setting(TYPE, "ul")) ? "ol" : "ul";
        StringBuilder markup = new StringBuilder("<" + tag + " class=\"views-list\">");
        context.rows().forEach(row -> markup.append("<li class=\"views-row\">").append(context.row().apply(row))
                .append("</li>"));
        return markup.append("</").append(tag).append(">").toString();
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.select(prefix, TYPE, "List", settings, List.of(new SelectOption("ul", "Bulleted"),
                new SelectOption("ol", "Numbered"))));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(TYPE, Settings.chosen(prefix, TYPE, submitted, List.of("ul", "ol")));
    }
}
