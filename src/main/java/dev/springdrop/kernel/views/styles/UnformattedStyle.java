package dev.springdrop.kernel.views.styles;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.StyleContext;
import dev.springdrop.kernel.views.StylePlugin;

/** Each result one after another, each in its own block. */
@SpringDropPlugin(id = UnformattedStyle.ID, type = StylePlugin.class)
public class UnformattedStyle implements StylePlugin {

    public static final String ID = "default";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Unformatted list";
    }

    @Override
    public String render(StyleContext context) {
        StringBuilder markup = new StringBuilder("<div class=\"views-rows\">");
        context.rows().forEach(row -> markup.append("<div class=\"views-row mb-3\">")
                .append(context.row().apply(row)).append("</div>"));
        return markup.append("</div>").toString();
    }
}
