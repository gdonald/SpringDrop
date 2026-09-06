package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

/**
 * The label of the entity a reference points at, linked to it by default. A
 * reference to something that is no longer there shows its id, so the dangling
 * reference is visible rather than silently blank.
 */
@SpringDropPlugin(id = EntityReferenceLabelFormatter.ID, type = FieldFormatter.class)
public class EntityReferenceLabelFormatter implements FieldFormatter {

    public static final String ID = "entity_reference_label";

    /** Whether the label is a link to the target. */
    public static final String LINK = "link";

    /** The route link a target is reached by. */
    public static final String CANONICAL = "canonical";

    private final EntityCrudService entities;
    private final EntityTypeManager entityTypeManager;

    public EntityReferenceLabelFormatter(EntityCrudService entities, EntityTypeManager entityTypeManager) {
        this.entities = entities;
        this.entityTypeManager = entityTypeManager;
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

        String label = HtmlUtils.htmlEscape(String.valueOf(target.get().label()));
        if (!Boolean.TRUE.equals(context.setting(LINK, true))) {
            return label;
        }
        return path(targetType, value)
                .map(href -> "<a href=\"" + HtmlUtils.htmlEscape(href) + "\">" + label + "</a>")
                .orElse(label);
    }

    /** Where the target is read, from the type's canonical route link. */
    private Optional<String> path(String targetType, Object id) {
        EntityType type = entityTypeManager.require(targetType);
        return Optional.ofNullable(type.links().get(CANONICAL))
                .map(link -> link.replace("{" + targetType + "}", String.valueOf(id)));
    }
}
