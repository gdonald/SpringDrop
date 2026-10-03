package dev.springdrop.kernel.filter.filters;

import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.web.util.HtmlUtils;

/**
 * Turns web and email addresses written in text into links. Addresses already
 * inside a link are left alone, and a link's text is cut to the {@code length}
 * setting, with the whole address kept in the link itself.
 */
@SpringDropPlugin(id = UrlFilter.ID, type = TextFilter.class)
public class UrlFilter implements TextFilter {

    public static final String ID = "filter_url";

    public static final String LENGTH = "length";

    public static final int DEFAULT_LENGTH = 72;

    private static final Pattern ADDRESS = Pattern.compile(
            "(https?://[^\\s<>\"]+[^\\s<>\".,;:!?)\\]])|(www\\.[^\\s<>\"]+[^\\s<>\".,;:!?)\\]])"
                    + "|([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    @Override
    public String label() {
        return "Convert URLs into links";
    }

    @Override
    public Map<String, Object> defaultSettings() {
        return Map.of(LENGTH, DEFAULT_LENGTH);
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(FormElement.of(ElementType.NUMBER, prefix + LENGTH)
                .label("Maximum link text length")
                .description("Longer addresses are shown cut to this many characters.")
                .value(settings.getOrDefault(LENGTH, DEFAULT_LENGTH))
                .rule(ValidationRule.pattern("\\d{1,4}").withMessage("The length is a whole number.")));
    }

    /** The length a submission gives, or the default when it is not a whole number. */
    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        String length = submitted.getOrDefault(prefix + LENGTH, "");
        return Map.of(LENGTH, length.matches("\\d{1,4}") ? Integer.parseInt(length) : DEFAULT_LENGTH);
    }

    @Override
    public String process(String text, Map<String, Object> settings) {
        int length = Integer.parseInt(String.valueOf(settings.getOrDefault(LENGTH, DEFAULT_LENGTH)));
        Document document = Jsoup.parseBodyFragment(text);
        document.outputSettings().prettyPrint(false);
        linkText(document.body(), length);
        return document.body().html();
    }

    private static void linkText(Element element, int length) {
        for (TextNode node : element.textNodes()) {
            String linked = linked(node.getWholeText(), length);
            if (!linked.equals(HtmlUtils.htmlEscape(node.getWholeText()))) {
                node.before(linked);
                node.remove();
            }
        }
        for (Element child : element.children()) {
            if (!child.tagName().equals("a")) {
                linkText(child, length);
            }
        }
    }

    private static String linked(String text, int length) {
        StringBuilder html = new StringBuilder();
        Matcher matcher = ADDRESS.matcher(text);
        int last = 0;
        while (matcher.find()) {
            html.append(HtmlUtils.htmlEscape(text.substring(last, matcher.start())));
            String address = matcher.group();
            String href = (matcher.group(3) != null) ? "mailto:" + address
                    : (matcher.group(2) != null) ? "http://" + address : address;
            String shown = (address.length() > length) ? address.substring(0, length) + "..." : address;
            html.append("<a href=\"").append(HtmlUtils.htmlEscape(href)).append("\">")
                    .append(HtmlUtils.htmlEscape(shown)).append("</a>");
            last = matcher.end();
        }
        return html.append(HtmlUtils.htmlEscape(text.substring(last))).toString();
    }
}
