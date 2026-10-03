package dev.springdrop.kernel.filter.filters;

import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/** Shows every tag as written rather than as HTML, the step plain text starts with. */
@SpringDropPlugin(id = HtmlEscapeFilter.ID, type = TextFilter.class)
public class HtmlEscapeFilter implements TextFilter {

    public static final String ID = "filter_html_escape";

    @Override
    public String label() {
        return "Display any HTML as plain text";
    }

    @Override
    public String process(String text, Map<String, Object> settings) {
        return HtmlUtils.htmlEscape(text);
    }
}
