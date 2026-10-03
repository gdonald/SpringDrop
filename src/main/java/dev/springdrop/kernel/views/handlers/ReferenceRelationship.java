package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.RelationshipHandler;
import dev.springdrop.kernel.views.Settings;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reaches the entity a reference property names: an entity reference field,
 * whose storage names the target type, or the {@code owner} base field, which
 * names a user. The {@code target_type} setting names the type for any other
 * property holding an id.
 */
@SpringDropPlugin(id = ReferenceRelationship.ID, type = RelationshipHandler.class)
public class ReferenceRelationship implements RelationshipHandler {

    public static final String ID = "reference";

    public static final String TARGET_TYPE = "target_type";

    private final FieldConfigManager fields;

    public ReferenceRelationship(FieldConfigManager fields) {
        this.fields = fields;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Reference";
    }

    @Override
    public String targetType(String baseEntityType, HandlerConfig config) {
        String named = config.text(TARGET_TYPE);
        if (!named.isEmpty()) {
            return named;
        }
        return fields.findStorage(baseEntityType, config.property())
                .map(EntityReferenceFieldType::targetType)
                .orElse(config.property().equals(BaseFieldDefinition.OWNER) ? "user" : "");
    }

    @Override
    public Optional<Object> targetId(EntityData entity, HandlerConfig config) {
        Object value = entity.fields().get(config.property());
        if (value instanceof List<?> list) {
            value = list.isEmpty() ? null : list.getFirst();
        }
        return Optional.ofNullable(value).map(String::valueOf).filter(id -> id.matches("\\d{1,18}"))
                .map(Long::valueOf);
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, TARGET_TYPE, "Entity type reached, when the property does not say",
                settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        String target = Settings.submitted(prefix, TARGET_TYPE, submitted);
        return target.isEmpty() ? Map.of() : Map.of(TARGET_TYPE, target);
    }
}
