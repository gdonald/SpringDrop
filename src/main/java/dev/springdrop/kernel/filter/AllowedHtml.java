package dev.springdrop.kernel.filter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tags and attributes a format keeps, written the way the allowed-HTML
 * filter's setting is: each tag in angle brackets with the attributes it may
 * carry, such as {@code <a href hreflang> <em> <p>}.
 *
 * <p>Some tags and attributes are never kept whatever the setting says: scripts,
 * styles, frames, objects, forms, and event handlers.
 */
public record AllowedHtml(Map<String, Set<String>> tags) {

    /** Tags no format keeps, since they run code or pull in other pages. */
    public static final Set<String> NEVER_TAGS = Set.of(
            "script", "style", "iframe", "frame", "frameset", "object", "embed", "applet", "form", "input",
            "button", "textarea", "select", "meta", "link", "base", "svg", "math");

    private static final Pattern TAG = Pattern.compile("<\\s*([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");

    /**
     * What a format with no allowed-HTML filter keeps: the tags written content
     * uses, with the attributes they need.
     */
    public static final AllowedHtml BROAD = parse("<a href hreflang title> <abbr title> <b> <blockquote cite> <br> "
            + "<caption> <cite> <code> <col span> <colgroup span> <dd> <del> <div> <dl> <dt> <em> <figcaption> "
            + "<figure> <h1 id> <h2 id> <h3 id> <h4 id> <h5 id> <h6 id> <hr> <i> <img src alt title width height> "
            + "<ins> <li> <ol start type> <p> <pre> <q cite> <s> <small> <span> <strong> <sub> <sup> <table> "
            + "<tbody> <td colspan rowspan> <tfoot> <th colspan rowspan scope> <thead> <tr> <u> <ul type>");

    public AllowedHtml {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        tags.forEach((tag, attributes) -> copy.put(tag, Collections.unmodifiableSet(new LinkedHashSet<>(attributes))));
        tags = Collections.unmodifiableMap(copy);
    }

    /**
     * The tags a setting names, leaving out the ones no format keeps, and
     * leaving out every event handler and style attribute.
     */
    public static AllowedHtml parse(String setting) {
        Map<String, Set<String>> tags = new LinkedHashMap<>();
        Matcher matcher = TAG.matcher(setting);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase();
            if (NEVER_TAGS.contains(tag)) {
                continue;
            }
            Set<String> attributes = new LinkedHashSet<>();
            for (String attribute : matcher.group(2).trim().split("\\s+")) {
                String name = attribute.toLowerCase();
                if (!name.isEmpty() && !name.startsWith("on") && !name.equals("style")) {
                    attributes.add(name);
                }
            }
            tags.merge(tag, attributes, (first, second) -> {
                Set<String> merged = new LinkedHashSet<>(first);
                merged.addAll(second);
                return merged;
            });
        }
        return new AllowedHtml(tags);
    }

    /** The tags in the order the setting names them. */
    public List<String> tagNames() {
        return List.copyOf(tags.keySet());
    }
}
