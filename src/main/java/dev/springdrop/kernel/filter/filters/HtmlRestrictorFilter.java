package dev.springdrop.kernel.filter.filters;

import dev.springdrop.kernel.filter.AllowedHtml;
import dev.springdrop.kernel.filter.HtmlSanitizer;
import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import java.util.List;
import java.util.Map;

/** Keeps only the HTML tags and attributes its {@code allowed_html} setting names. */
@SpringDropPlugin(id = HtmlRestrictorFilter.ID, type = TextFilter.class)
public class HtmlRestrictorFilter implements TextFilter {

    public static final String ID = "filter_html";

    public static final String ALLOWED_HTML = "allowed_html";

    static final int ALLOWED_HTML_MAX_LENGTH = 4000;

    /** What a new allowed-HTML filter keeps: the tags of plain, structured writing. */
    public static final String RESTRICTED_TAGS = "<a href hreflang> <em> <strong> <cite> <blockquote cite> <code> "
            + "<ul type> <ol start type> <li> <dl> <dt> <dd> <h2 id> <h3 id> <h4 id> <h5 id> <h6 id> <p> <br>";

    private final HtmlSanitizer sanitizer;

    public HtmlRestrictorFilter(HtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String label() {
        return "Limit allowed HTML tags and correct faulty HTML";
    }

    @Override
    public String process(String text, Map<String, Object> settings) {
        return sanitizer.sanitize(text, AllowedHtml.parse(String.valueOf(settings.getOrDefault(ALLOWED_HTML, ""))));
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(FormElement.of(ElementType.TEXTAREA, prefix + ALLOWED_HTML)
                .label("Allowed HTML tags")
                .description("Each tag in angle brackets with the attributes it may carry, such as <a href>. "
                        + "Scripts, styles, frames, forms, and event handlers are never kept.")
                .value(String.valueOf(settings.getOrDefault(ALLOWED_HTML, "")))
                .rule(ValidationRule.maxLength(ALLOWED_HTML_MAX_LENGTH)));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(ALLOWED_HTML, submitted.getOrDefault(prefix + ALLOWED_HTML, ""));
    }

    @Override
    public Map<String, Object> defaultSettings() {
        return Map.of(ALLOWED_HTML, RESTRICTED_TAGS);
    }
}
