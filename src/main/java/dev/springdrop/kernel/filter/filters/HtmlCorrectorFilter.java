package dev.springdrop.kernel.filter.filters;

import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Closes tags left open and drops stray closing tags, so faulty HTML cannot break the page around it. */
@SpringDropPlugin(id = HtmlCorrectorFilter.ID, type = TextFilter.class)
public class HtmlCorrectorFilter implements TextFilter {

    public static final String ID = "filter_htmlcorrector";

    @Override
    public String label() {
        return "Correct faulty and chopped off HTML";
    }

    @Override
    public String process(String text, Map<String, Object> settings) {
        Document document = Jsoup.parseBodyFragment(text);
        document.outputSettings().prettyPrint(false);
        return document.body().html();
    }
}
