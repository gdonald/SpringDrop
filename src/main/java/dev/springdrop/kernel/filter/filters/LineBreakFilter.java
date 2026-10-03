package dev.springdrop.kernel.filter.filters;

import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns line breaks into HTML: a blank line between two runs of text makes them
 * separate paragraphs, and a single line break becomes a {@code <br>}. A run
 * that already starts with a block of HTML, such as a list or a heading, is
 * left as it is.
 */
@SpringDropPlugin(id = LineBreakFilter.ID, type = TextFilter.class)
public class LineBreakFilter implements TextFilter {

    public static final String ID = "filter_autop";

    private static final Pattern BLOCK_START = Pattern.compile(
            "(?is)^<(p|div|ul|ol|dl|h[1-6]|blockquote|pre|table|figure|hr)\\b.*");

    @Override
    public String label() {
        return "Convert line breaks into HTML";
    }

    @Override
    public String process(String text, Map<String, Object> settings) {
        StringBuilder html = new StringBuilder();
        for (String run : text.replace("\r\n", "\n").split("\n\\s*\n")) {
            String trimmed = run.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            html.append(BLOCK_START.matcher(trimmed).matches()
                    ? trimmed
                    : "<p>" + trimmed.replace("\n", "<br>\n") + "</p>").append('\n');
        }
        return html.toString().strip();
    }
}
