package dev.springdrop.kernel.render;

import java.util.ArrayList;
import java.util.List;

/**
 * What a piece of output needs the page to carry for it: style sheets, scripts,
 * and anything else that belongs in the head. Attachments bubble the same way
 * cacheability does, and each one is carried once however many times it is
 * asked for.
 */
public record Attachments(List<String> styleSheets, List<String> scripts, List<String> headTags) {

    public static final Attachments NONE = new Attachments(List.of(), List.of(), List.of());

    public Attachments {
        styleSheets = List.copyOf(styleSheets);
        scripts = List.copyOf(scripts);
        headTags = List.copyOf(headTags);
    }

    public Attachments withStyleSheet(String href) {
        return new Attachments(plus(styleSheets, href), scripts, headTags);
    }

    public Attachments withScript(String src) {
        return new Attachments(styleSheets, plus(scripts, src), headTags);
    }

    public Attachments withHeadTag(String markup) {
        return new Attachments(styleSheets, scripts, plus(headTags, markup));
    }

    public Attachments merge(Attachments other) {
        return new Attachments(
                union(styleSheets, other.styleSheets),
                union(scripts, other.scripts),
                union(headTags, other.headTags));
    }

    private static List<String> plus(List<String> values, String value) {
        return union(values, List.of(value));
    }

    private static List<String> union(List<String> first, List<String> second) {
        List<String> combined = new ArrayList<>(first);
        second.stream().filter(value -> !combined.contains(value)).forEach(combined::add);
        return combined;
    }
}
