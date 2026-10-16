package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.render.Renderable;
import java.util.Map;

/**
 * The contextual links a block or a piece of content carries, such as
 * "Configure block" or "Edit", for those who may follow them. They are a
 * placeholder built for each request, drawn in the {@code contextual} slot of
 * the block or content template, so a kept page shows each person their own.
 */
public final class Contextual {

    /** The placeholder builder that draws the links. */
    public static final String PLACEHOLDER = "contextual_links";

    public static final String SLOT = "contextual";

    public static final String KIND = "kind";

    public static final String ID = "id";

    public static final String BLOCK = "block";

    public static final String NODE = "node";

    private Contextual() {
    }

    /** The links of one block placement or one node, built when the page around them is done. */
    public static Renderable links(String kind, Object id) {
        return Renderable.placeholder(PLACEHOLDER, Map.of(KIND, kind, ID, String.valueOf(id))).inSlot(SLOT);
    }
}
