package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.LinkResolver;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.constraints.LinkConstraint;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/**
 * A link as an anchor, shown by its title or, when it has none, by where it
 * goes. An internal link points at the path its route serves; an external one
 * opens in a new tab and is marked so the page it opens cannot reach back into
 * this one. A long address can be trimmed so it does not run off the line.
 */
@SpringDropPlugin(id = LinkFormatter.ID, type = FieldFormatter.class)
public class LinkFormatter implements FieldFormatter {

    public static final String ID = "link";

    /** How many characters of the shown text to keep; 0 keeps all of it. */
    public static final String TRIM_LENGTH = "trim_length";

    private static final String ELLIPSIS = "...";

    private final LinkResolver linkResolver;

    public LinkFormatter(LinkResolver linkResolver) {
        this.linkResolver = linkResolver;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        if (!(value instanceof Map<?, ?> link)) {
            return HtmlUtils.htmlEscape(String.valueOf(value));
        }

        String uri = String.valueOf(link.get(LinkConstraint.URI_KEY));
        String href = href(uri);
        String shown = trimmed(context, title(link, href));
        boolean external = linkResolver.isExternal(link);

        return "<a href=\"" + HtmlUtils.htmlEscape(href) + "\""
                + (external ? " target=\"_blank\" rel=\"noopener noreferrer\"" : "")
                + ">" + HtmlUtils.htmlEscape(shown) + "</a>";
    }

    private String href(String uri) {
        return uri.startsWith(LinkConstraint.INTERNAL_SCHEME) ? LinkConstraint.internalPath(uri) : uri;
    }

    private static String title(Map<?, ?> link, String fallback) {
        Object title = link.get(LinkConstraint.TITLE_KEY);
        return (title == null || title.toString().isBlank()) ? fallback : title.toString();
    }

    private static String trimmed(FormatterContext context, String text) {
        int limit = ((Number) context.setting(TRIM_LENGTH, 0)).intValue();
        if (limit <= 0 || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + ELLIPSIS;
    }
}
