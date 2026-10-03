package dev.springdrop.kernel.media;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.RenderService;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Draws media embedded in text as
 * {@code <media-embed data-entity-uuid="..." data-view-mode="..."></media-embed>}:
 * the media item with that uuid in that view mode, {@code default} when none is
 * named. Before the sanitizer runs, each embed is swapped for a marker holding
 * a secret made fresh each time the site starts, which text cannot guess; after
 * it, each marker is swapped for the item as the site draws it, so the item's
 * own markup is not taken apart. An item that is gone, or that the reader may
 * not view, draws nothing. An item embedding itself, at any depth, is drawn
 * once.
 */
@SpringDropPlugin(id = TextFormatManager.MEDIA_EMBED_FILTER, type = TextFilter.class)
public class MediaEmbedFilter implements TextFilter {

    private static final Pattern EMBED = Pattern.compile(
            "<media-embed\\b([^>]*)>(\\s*</media-embed>)?", Pattern.CASE_INSENSITIVE);

    private static final String UUID_SHAPE = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private static final Pattern UUID_ATTRIBUTE = Pattern.compile(
            "data-entity-uuid\\s*=\\s*[\"'](" + UUID_SHAPE + ")[\"']");

    private static final Pattern VIEW_MODE_ATTRIBUTE = Pattern.compile(
            "data-view-mode\\s*=\\s*[\"']([a-z0-9_]{1,64})[\"']");

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /** How deep embeds inside embedded media are drawn. */
    static final int MAX_DEPTH = 3;

    private final String secret = secret();

    private final Pattern marker = Pattern.compile(
            "\\[media-embed-" + secret + ":(" + UUID_SHAPE + "):([a-z0-9_]{1,64})]");

    private final ObjectProvider<MediaService> media;
    private final ObjectProvider<EntityQueryExecutor> queries;
    private final ObjectProvider<EntityAccessManager> access;
    private final ObjectProvider<RenderService> renderer;

    public MediaEmbedFilter(ObjectProvider<MediaService> media, ObjectProvider<EntityQueryExecutor> queries,
            ObjectProvider<EntityAccessManager> access, ObjectProvider<RenderService> renderer) {
        this.media = media;
        this.queries = queries;
        this.access = access;
        this.renderer = renderer;
    }

    private static String secret() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** The embed code of a media item, drawn in the default view mode. */
    public static String embedCode(Object uuid) {
        return "<media-embed data-entity-uuid=\"" + uuid + "\" data-view-mode=\"" + ViewDisplayConfig.DEFAULT_MODE
                + "\"></media-embed>";
    }

    @Override
    public String label() {
        return "Embed media";
    }

    @Override
    public String process(String text, Map<String, Object> settings) {
        Matcher embed = EMBED.matcher(text);
        StringBuilder swapped = new StringBuilder();
        while (embed.find()) {
            Matcher uuid = UUID_ATTRIBUTE.matcher(embed.group(1));
            Matcher viewMode = VIEW_MODE_ATTRIBUTE.matcher(embed.group(1));
            String replacement = uuid.find()
                    ? "[media-embed-" + secret + ":" + uuid.group(1).toLowerCase(Locale.ROOT) + ":"
                            + (viewMode.find() ? viewMode.group(1) : ViewDisplayConfig.DEFAULT_MODE) + "]"
                    : "";
            embed.appendReplacement(swapped, Matcher.quoteReplacement(replacement));
        }
        embed.appendTail(swapped);
        return swapped.toString();
    }

    @Override
    public String afterSanitizing(String html, Map<String, Object> settings) {
        Matcher found = marker.matcher(html);
        StringBuilder drawn = new StringBuilder();
        while (found.find()) {
            found.appendReplacement(drawn, Matcher.quoteReplacement(draw(found.group(1), found.group(2))));
        }
        found.appendTail(drawn);
        return drawn.toString();
    }

    private String draw(String uuid, String viewMode) {
        if (DEPTH.get() >= MAX_DEPTH) {
            return "";
        }
        Optional<EntityData> item = queries.getObject().query(MediaEntityType.ID)
                .condition(Condition.equal("uuid", UUID.fromString(uuid))).ids().stream().findFirst()
                .flatMap(id -> media.getObject().find(((Number) id).longValue()))
                .filter(found -> access.getObject().may(MediaEntityType.ID, found, EntityAccessHandler.VIEW));
        if (item.isEmpty()) {
            return "";
        }
        DEPTH.set(DEPTH.get() + 1);
        try {
            return renderer.getObject().render(media.getObject().build(item.get(), viewMode, false)).html();
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }
}
