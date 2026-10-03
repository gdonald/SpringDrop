package dev.springdrop.kernel.render;

import java.util.Map;

/**
 * Builds a named placeholder's content for the current request, such as a
 * greeting naming the account signed in. A page kept in the dynamic page cache
 * keeps its named placeholders as markers, and each is built again for every
 * request that uses the page. A module adds one by registering a bean of this
 * type.
 */
public interface PlaceholderBuilder {

    String id();

    Renderable build(Map<String, String> arguments);
}
