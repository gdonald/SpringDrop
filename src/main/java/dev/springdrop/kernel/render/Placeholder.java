package dev.springdrop.kernel.render;

import java.util.Map;

/**
 * A placeholder built by the {@link PlaceholderBuilder} the id names, with
 * arguments, so a kept page can build it again for another request. The
 * {@link RenderService} builds it, never this object.
 */
public record Placeholder(String builderId, Map<String, String> arguments) implements LazyBuilder {

    public Placeholder {
        arguments = Map.copyOf(arguments);
    }

    @Override
    public Renderable build() {
        throw new UnsupportedOperationException("The placeholder " + builderId
                + " is built by the render service through its builder.");
    }
}
