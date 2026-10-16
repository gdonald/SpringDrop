package dev.springdrop.kernel.jsonapi;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityKeys;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.BooleanFieldType;
import dev.springdrop.kernel.field.types.DecimalFieldType;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.types.FloatFieldType;
import dev.springdrop.kernel.field.types.IntegerFieldType;
import dev.springdrop.kernel.field.types.ListFloatFieldType;
import dev.springdrop.kernel.field.types.ListIntegerFieldType;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.user.UserEntityType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The members of a content entity's resource object. Its relationships are the
 * owner and each entity reference field. Its attributes are its internal id,
 * label, language, and every other base and configurable field. A filter value
 * is read as the type of the column or field it compares.
 */
@Component
public class JsonApiSchema {

    /** The attribute holding the entity's numeric id, beside the resource's uuid. */
    public static final String INTERNAL_ID = "drupal_internal__id";

    private static final Set<String> WHOLE_NUMBER_FIELDS = Set.of(IntegerFieldType.ID, ListIntegerFieldType.ID,
            EntityReferenceFieldType.ID);

    private static final Set<String> DECIMAL_FIELDS = Set.of(FloatFieldType.ID, DecimalFieldType.ID,
            ListFloatFieldType.ID);

    private final FieldConfigManager fields;

    public JsonApiSchema(FieldConfigManager fields) {
        this.fields = fields;
    }

    /** Each relationship of the entity type, whatever the bundle, and the entity type it points at. */
    public Map<String, String> relationships(EntityType type) {
        Map<String, String> relationships = new LinkedHashMap<>();
        if (type.baseFields().stream().anyMatch(field -> field.name().equals(BaseFieldDefinition.OWNER))) {
            relationships.put(BaseFieldDefinition.OWNER, UserEntityType.ID);
        }
        for (FieldStorageConfig storage : fields.storages(type.id())) {
            String target = EntityReferenceFieldType.targetType(storage);
            if (storage.type().equals(EntityReferenceFieldType.ID) && !target.isEmpty()) {
                relationships.put(storage.name(), target);
            }
        }
        return relationships;
    }

    /** The relationships the bundle's entities carry. */
    public Map<String, String> relationships(EntityType type, String bundle) {
        List<String> configurable = configurable(type, bundle);
        Map<String, String> relationships = new LinkedHashMap<>(relationships(type));
        relationships.keySet().removeIf(name -> !name.equals(BaseFieldDefinition.OWNER)
                && !configurable.contains(name));
        return relationships;
    }

    /** Whether a relationship holds one entity rather than a list. */
    public boolean singleValued(EntityType type, String relationship) {
        return relationship.equals(BaseFieldDefinition.OWNER)
                || fields.findStorage(type.id(), relationship).map(storage -> storage.cardinality() == 1)
                        .orElse(true);
    }

    /** The attributes the bundle's entities carry, in the order they are sent. */
    public List<String> attributes(EntityType type, String bundle) {
        EntityKeys keys = type.keys();
        List<String> attributes = new ArrayList<>(List.of(INTERNAL_ID, keys.label(), keys.langcode()));
        Set<String> relationships = relationships(type).keySet();
        type.baseFields().stream().map(BaseFieldDefinition::name).filter(name -> !relationships.contains(name))
                .forEach(attributes::add);
        configurable(type, bundle).stream().filter(name -> !relationships.contains(name)).forEach(attributes::add);
        return List.copyOf(attributes);
    }

    /** The property an attribute is stored as, when the bundle's entities carry it. */
    public Optional<String> property(EntityType type, String bundle, String attribute) {
        if (attribute.equals(INTERNAL_ID)) {
            return Optional.of(type.keys().id());
        }
        return attributes(type, bundle).contains(attribute) ? Optional.of(attribute) : Optional.empty();
    }

    /** Whether a property is one of the keys every entity has, which no field access rule governs. */
    public static boolean isKey(EntityType type, String property) {
        EntityKeys keys = type.keys();
        return property.equals(keys.id()) || property.equals(keys.label()) || property.equals(keys.langcode());
    }

    /** A filter's text value as the property compares it. A value the property cannot hold is refused. */
    public Object filterValue(EntityType type, String property, String value) {
        if (property.equals(type.keys().id())) {
            return wholeNumber(property, value);
        }
        Optional<BaseFieldDefinition> base = type.baseFields().stream()
                .filter(field -> field.name().equals(property)).findFirst();
        if (base.isPresent()) {
            return baseValue(base.get().type(), property, value);
        }
        return fields.findStorage(type.id(), property).map(storage -> fieldValue(storage.type(), property, value))
                .orElse(value);
    }

    private static Object baseValue(ColumnType column, String property, String value) {
        return switch (column) {
            case BOOLEAN -> truth(property, value);
            case BIGINT -> wholeNumber(property, value);
            case INTEGER -> {
                if (!value.matches("-?\\d{1,9}")) {
                    throw refused(property, "a whole number");
                }
                yield Integer.parseInt(value);
            }
            case TIMESTAMP -> {
                try {
                    yield OffsetDateTime.parse(value);
                } catch (DateTimeParseException unreadable) {
                    throw refused(property, "a date and time with an offset, such as 2026-10-15T09:00:00Z");
                }
            }
            case UUID -> {
                try {
                    yield UUID.fromString(value);
                } catch (IllegalArgumentException unreadable) {
                    throw refused(property, "a uuid");
                }
            }
            case JSONB -> throw new JsonApiException(HttpStatus.BAD_REQUEST,
                    "The attribute " + property + " cannot be filtered or sorted on.");
            case TEXT, VARCHAR -> value;
        };
    }

    private static Object fieldValue(String fieldType, String property, String value) {
        if (WHOLE_NUMBER_FIELDS.contains(fieldType)) {
            return wholeNumber(property, value);
        }
        if (DECIMAL_FIELDS.contains(fieldType)) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException unreadable) {
                throw refused(property, "a number");
            }
        }
        if (fieldType.equals(BooleanFieldType.ID)) {
            return truth(property, value);
        }
        return value;
    }

    private static long wholeNumber(String property, String value) {
        if (!value.matches("-?\\d{1,18}")) {
            throw refused(property, "a whole number");
        }
        return Long.parseLong(value);
    }

    private static boolean truth(String property, String value) {
        return switch (value) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw refused(property, "true or false");
        };
    }

    private static JsonApiException refused(String property, String kind) {
        return new JsonApiException(HttpStatus.BAD_REQUEST, "The filter value for " + property + " is not " + kind
                + ".");
    }

    private List<String> configurable(EntityType type, String bundle) {
        return fields.fieldNames(type.id(), type.bundleEntityType() == null ? type.id() : bundle);
    }
}
