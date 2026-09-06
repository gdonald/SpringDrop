package dev.springdrop.kernel.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Turns a {@link Renderable} tree into markup. Each node is drawn by its
 * Thymeleaf fragment, which reads the node's data, its attributes, and the
 * already-rendered markup of its children as {@code children}.
 *
 * <p>Cacheability and attachments bubble: a node's own metadata is merged with
 * everything below it, so the page ends up carrying every tag any part of it
 * depends on. A lazy node renders as a placeholder and is built afterwards, and
 * what it builds contributes its own metadata once it is in place.
 */
@Component
public class RenderService {

    public static final String PLACEHOLDER_ATTRIBUTE = "data-render-placeholder";

    private final ITemplateEngine templateEngine;

    public RenderService(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public RenderedPage render(Renderable root) {
        Bubble bubble = new Bubble();
        String html = fill(draw(root, bubble), bubble);
        return new RenderedPage(html, bubble.cache, bubble.attachments);
    }

    private String draw(Renderable node, Bubble bubble) {
        if (node.lazyBuilder().isPresent()) {
            return bubble.placeholderFor(node.lazyBuilder().get());
        }

        bubble.absorb(node);

        StringBuilder children = new StringBuilder();
        for (Renderable child : node.children()) {
            children.append(draw(child, bubble));
        }

        Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariables(node.data());
        context.setVariable("attributes", node.attributes());
        context.setVariable("children", children.toString());
        return templateEngine.process(node.template(), Set.of(node.type()), context);
    }

    /**
     * Replaces each placeholder with what its builder produced. A built part may
     * hold placeholders of its own, so this runs until none are left.
     */
    private String fill(String shell, Bubble bubble) {
        String html = shell;
        while (!bubble.pending.isEmpty()) {
            Map<String, LazyBuilder> round = new LinkedHashMap<>(bubble.pending);
            bubble.pending.clear();
            for (Map.Entry<String, LazyBuilder> entry : round.entrySet()) {
                html = html.replace(marker(entry.getKey()), draw(entry.getValue().build(), bubble));
            }
        }
        return html;
    }

    private static String marker(String id) {
        return "<span " + PLACEHOLDER_ATTRIBUTE + "=\"" + id + "\"></span>";
    }

    /** What has bubbled up so far, and what is still waiting to be built. */
    private static final class Bubble {

        private final Map<String, LazyBuilder> pending = new LinkedHashMap<>();
        private CacheMetadata cache = CacheMetadata.EMPTY;
        private Attachments attachments = Attachments.NONE;
        private int placeholders;

        private void absorb(Renderable node) {
            cache = cache.merge(node.cache());
            attachments = attachments.merge(node.attachments());
        }

        private String placeholderFor(LazyBuilder builder) {
            String id = "placeholder-" + (++placeholders);
            pending.put(id, builder);
            return marker(id);
        }
    }
}
