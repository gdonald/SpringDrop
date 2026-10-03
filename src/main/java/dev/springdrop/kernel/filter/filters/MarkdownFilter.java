package dev.springdrop.kernel.filter.filters;

import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Renders Markdown as HTML, following CommonMark. Links to addresses that are
 * not web or mail addresses are dropped as the HTML is written.
 */
@SpringDropPlugin(id = MarkdownFilter.ID, type = TextFilter.class)
public class MarkdownFilter implements TextFilter {

    public static final String ID = "filter_markdown";

    private final Parser parser = Parser.builder().build();

    private final HtmlRenderer renderer = HtmlRenderer.builder().sanitizeUrls(true).build();

    @Override
    public String label() {
        return "Render Markdown";
    }

    @Override
    public String process(String text, Map<String, Object> settings) {
        return renderer.render(parser.parse(text)).strip();
    }
}
