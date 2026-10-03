package dev.springdrop.kernel.views.rows;

import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.media.MediaEntityType;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.RowPlugin;
import dev.springdrop.kernel.views.Settings;
import dev.springdrop.kernel.views.ViewOptions;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The result rendered whole in the view mode the {@code view_mode} setting
 * names, {@code teaser} by default: a node or media item through its template,
 * and any other entity through its view display.
 */
@SpringDropPlugin(id = EntityRow.ID, type = RowPlugin.class)
public class EntityRow implements RowPlugin {

    public static final String ID = "entity";

    public static final String VIEW_MODE = "view_mode";

    private final NodeService nodes;
    private final MediaService media;
    private final ViewDisplayManager displays;
    private final RenderService renderer;

    public EntityRow(NodeService nodes, MediaService media, ViewDisplayManager displays, RenderService renderer) {
        this.nodes = nodes;
        this.media = media;
        this.displays = displays;
        this.renderer = renderer;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Rendered entity";
    }

    @Override
    public String render(ResultRow row, ViewOptions options, PluginConfig config,
            BiFunction<ResultRow, HandlerConfig, String> field, Function<HandlerConfig, String> heading) {
        String mode = String.valueOf(config.setting(VIEW_MODE, ViewDisplayConfig.TEASER_MODE));
        return switch (row.entity().entityType()) {
            case NodeEntityType.ID -> renderer.render(nodes.build(row.entity(), mode, false)).html();
            case MediaEntityType.ID -> renderer.render(media.build(row.entity(), mode, false)).html();
            default -> displays.render(row.entity(), mode);
        };
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, VIEW_MODE, "View mode", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        String mode = Settings.submitted(prefix, VIEW_MODE, submitted);
        return Map.of(VIEW_MODE, mode.isEmpty() ? ViewDisplayConfig.TEASER_MODE : mode);
    }
}
