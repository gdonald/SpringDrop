package dev.springdrop.kernel.filter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Shortens HTML to a number of characters of its text, cutting at the last
 * whole word that fits, closing every tag left open and dropping whatever
 * comes after the cut.
 */
public final class HtmlTrimmer {

    private HtmlTrimmer() {
    }

    public static String trim(String html, int length) {
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        if (document.body().text().length() <= length) {
            return document.body().html();
        }
        cut(document.body(), new int[] {length});
        return document.body().html();
    }

    /** Keeps the first {@code remaining[0]} characters of text under the node, removing the rest. */
    private static void cut(Node node, int[] remaining) {
        for (Node child : node.childNodes().toArray(Node[]::new)) {
            if (remaining[0] <= 0) {
                child.remove();
            } else if (child instanceof TextNode text) {
                String whole = text.getWholeText();
                if (whole.length() > remaining[0]) {
                    String kept = whole.substring(0, remaining[0]);
                    int space = kept.lastIndexOf(' ');
                    text.text((space > 0 ? kept.substring(0, space) : kept).stripTrailing() + "...");
                    remaining[0] = 0;
                } else {
                    remaining[0] -= whole.length();
                }
            } else {
                cut(child, remaining);
            }
        }
    }
}
