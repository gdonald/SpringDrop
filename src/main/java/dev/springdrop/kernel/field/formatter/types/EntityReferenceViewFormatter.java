package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

/**
 * The referenced entity rendered in full, in whichever view mode the display
 * names. An entity already being rendered further up the page is not rendered
 * again, so a reference that points back at its own page ends rather than
 * looping.
 */
@SpringDropPlugin(id = EntityReferenceViewFormatter.ID, type = FieldFormatter.class)
public class EntityReferenceViewFormatter implements FieldFormatter {

    public static final String ID = "entity_reference_entity_view";

    public static final String VIEW_MODE = "view_mode";

    private static final ThreadLocal<Deque<String>> RENDERING = ThreadLocal.withInitial(ArrayDeque::new);

    private final EntityCrudService entities;
    private final ViewDisplayManager displays;

    public EntityReferenceViewFormatter(EntityCrudService entities, ViewDisplayManager displays) {
        this.entities = entities;
        this.displays = displays;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        String targetType = EntityReferenceFieldType.targetType(context.storage());
        Optional<EntityData> target = entities.load(targetType, value);
        if (target.isEmpty()) {
            return HtmlUtils.htmlEscape(String.valueOf(value));
        }

        EntityData entity = target.get();
        String marker = targetType + ":" + entity.id();
        Deque<String> rendering = RENDERING.get();
        if (rendering.contains(marker)) {
            return "";
        }

        rendering.push(marker);
        try {
            String mode = context.text(VIEW_MODE, ViewDisplayConfig.DEFAULT_MODE);
            return "<div class=\"referenced-entity\">"
                    + displays.render(targetType, entity.bundle(), mode, entity.fields())
                    + "</div>";
        } finally {
            rendering.pop();
        }
    }
}
