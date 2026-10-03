package dev.springdrop.kernel.render;

import dev.springdrop.kernel.cache.RenderCache;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Turns a {@link Renderable} tree into markup. Each node is drawn by its
 * Thymeleaf fragment, which reads the node's data, its attributes, and the
 * already-rendered markup of its children as {@code children}. A child put in a
 * named slot is drawn into {@code slots} under that name instead, so a layout
 * places each region where it belongs.
 *
 * <p>A node naming no fragment is drawn by its whole template, which is how a
 * theme's templates are written: one file, one piece of output.
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
    private final RenderCache renderCache;
    private final Map<String, PlaceholderBuilder> placeholderBuilders;

    public RenderService(ITemplateEngine templateEngine, RenderCache renderCache,
            List<PlaceholderBuilder> placeholderBuilders) {
        this.templateEngine = templateEngine;
        this.renderCache = renderCache;
        this.placeholderBuilders = placeholderBuilders.stream()
                .collect(Collectors.toMap(PlaceholderBuilder::id, Function.identity()));
    }

    public RenderedPage render(Renderable root) {
        return fill(renderShell(root));
    }

    /** The drawing with a marker for each placeholder, its placeholders not yet built. */
    public Shell renderShell(Renderable root) {
        Bubble bubble = new Bubble(new LinkedHashMap<>(), new AtomicInteger());
        String html = draw(root, bubble);
        return new Shell(html, bubble.cache, bubble.attachments, bubble.pending, bubble.placeholders.get());
    }

    /** Builds a shell's placeholders into it, adding what each carries to what the shell carries. */
    public RenderedPage fill(Shell shell) {
        Bubble bubble = new Bubble(new LinkedHashMap<>(shell.placeholders()), new AtomicInteger(shell.numbered()));
        bubble.absorb(shell.cache(), shell.attachments());
        String html = fill(shell.html(), bubble);
        return new RenderedPage(html, bubble.cache, bubble.attachments);
    }

    private String draw(Renderable node, Bubble bubble) {
        if (node.lazyBuilder().isPresent()) {
            return bubble.placeholderFor(node.lazyBuilder().get());
        }
        if (node.cacheKeys().isEmpty()) {
            return drawNode(node, bubble);
        }
        Optional<RenderCache.Cached> kept = renderCache.get(node.cacheKeys(), node.cache().contexts());
        if (kept.isPresent()) {
            bubble.absorb(kept.get().cache(), kept.get().attachments());
            return kept.get().html();
        }
        Bubble own = new Bubble(bubble.pending, bubble.placeholders);
        int waiting = bubble.pending.size();
        String html = drawNode(node, own);
        if (bubble.pending.size() == waiting) {
            renderCache.put(node.cacheKeys(), node.cache().contexts(),
                    new RenderCache.Cached(html, own.cache, own.attachments));
        }
        bubble.absorb(own.cache, own.attachments);
        return html;
    }

    /**
     * Draws one node and its children. A node whose drawing holds a
     * placeholder is not kept in the render cache, since the placeholder is
     * filled in only for this drawing.
     */
    private String drawNode(Renderable node, Bubble bubble) {
        bubble.absorb(node.cache(), node.attachments());

        StringBuilder children = new StringBuilder();
        Map<String, String> slots = new LinkedHashMap<>();
        for (Renderable child : node.children()) {
            String markup = draw(child, bubble);
            if (child.slot().equals(Renderable.NO_SLOT)) {
                children.append(markup);
            } else {
                slots.merge(child.slot(), markup, String::concat);
            }
        }

        Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariables(node.data());
        context.setVariable("attributes", node.attributes());
        context.setVariable("children", children.toString());
        context.setVariable("slots", slots);
        return node.type().isEmpty()
                ? templateEngine.process(node.template(), context)
                : templateEngine.process(node.template(), Set.of(node.type()), context);
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
                html = html.replace(marker(entry.getKey()), draw(built(entry.getValue()), bubble));
            }
        }
        return html;
    }

    private Renderable built(LazyBuilder builder) {
        if (builder instanceof Placeholder named) {
            PlaceholderBuilder placeholder = placeholderBuilders.get(named.builderId());
            if (placeholder == null) {
                throw new IllegalStateException("The site has no placeholder builder " + named.builderId() + ".");
            }
            return placeholder.build(named.arguments());
        }
        return builder.build();
    }

    /** The marker a placeholder is drawn as in a shell. */
    public static String marker(String id) {
        return "<span " + PLACEHOLDER_ATTRIBUTE + "=\"" + id + "\"></span>";
    }

    /**
     * What has bubbled up so far, and what is still waiting to be built. A part
     * kept in the render cache gathers its own metadata in a bubble that shares
     * the waiting builders and the placeholder count with the page's.
     */
    private static final class Bubble {

        private final Map<String, LazyBuilder> pending;
        private final AtomicInteger placeholders;
        private CacheMetadata cache = CacheMetadata.EMPTY;
        private Attachments attachments = Attachments.NONE;

        private Bubble(Map<String, LazyBuilder> pending, AtomicInteger placeholders) {
            this.pending = pending;
            this.placeholders = placeholders;
        }

        private void absorb(CacheMetadata added, Attachments attached) {
            cache = cache.merge(added);
            attachments = attachments.merge(attached);
        }

        private String placeholderFor(LazyBuilder builder) {
            String id = "placeholder-" + placeholders.incrementAndGet();
            pending.put(id, builder);
            return marker(id);
        }
    }
}
