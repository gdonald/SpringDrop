package dev.springdrop.kernel.config.sync;

import dev.springdrop.kernel.entity.EntityKind;
import dev.springdrop.kernel.entity.EntityTypeManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Checks a config object's value against its schema: the type its value must
 * read as. A config entity's schema is its entity type's class, and other
 * objects' schemas come from the {@link ConfigSchemaProvider}s, an exact name
 * before a prefix. A value is refused when a key holds the wrong kind of
 * value, when a key the schema does not know is present, or when a key that
 * holds a number or a true or false value is null. An object with no schema
 * is not checked.
 */
@Component
public class ConfigSchemas {

    private final List<ConfigSchemaProvider> providers;
    private final EntityTypeManager entityTypes;
    private final ObjectMapper strict;

    public ConfigSchemas(List<ConfigSchemaProvider> providers, EntityTypeManager entityTypes,
            ObjectMapper objectMapper) {
        this.providers = List.copyOf(providers);
        this.entityTypes = entityTypes;
        this.strict = objectMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public Optional<Class<?>> schemaOf(String name) {
        List<ConfigSchema> schemas = new ArrayList<>();
        providers.forEach(provider -> schemas.addAll(provider.schemas()));
        entityTypes.all().stream().filter(type -> type.kind() == EntityKind.CONFIG)
                .forEach(type -> schemas.add(ConfigSchema.under(type.id(), type.entityClass())));
        return schemas.stream().filter(schema -> !schema.prefix() && schema.covers(name)).findFirst()
                .or(() -> schemas.stream().filter(schema -> schema.prefix() && schema.covers(name)).findFirst())
                .map(ConfigSchema::type);
    }

    /** What is wrong with the value, each problem naming the object and the key, or nothing. */
    public Optional<String> violation(String name, JsonNode data) {
        Optional<Class<?>> schema = schemaOf(name);
        if (schema.isEmpty()) {
            return Optional.empty();
        }
        try {
            strict.treeToValue(data, schema.get());
            return Optional.empty();
        } catch (UnrecognizedPropertyException unknown) {
            return Optional.of(name + ": " + path(unknown) + " is not a key of its schema.");
        } catch (InvalidFormatException wrong) {
            return Optional.of(name + ": " + path(wrong) + " holds " + wrong.getValue() + ", which is not "
                    + describe(wrong.getTargetType()) + ".");
        } catch (MismatchedInputException wrong) {
            return Optional.of(name + ": " + path(wrong) + " is not " + describe(wrong.getTargetType()) + ".");
        } catch (JacksonException unreadable) {
            return Optional.of(name + ": the value does not match its schema.");
        }
    }

    private static String path(JacksonException problem) {
        String path = problem.getPath().stream()
                .map(reference -> reference.getPropertyName() != null ? reference.getPropertyName()
                        : String.valueOf(reference.getIndex()))
                .collect(Collectors.joining("."));
        return path.isEmpty() ? "the value" : path;
    }

    private static final Map<Class<?>, String> KINDS = Map.ofEntries(
            Map.entry(int.class, "a whole number"), Map.entry(Integer.class, "a whole number"),
            Map.entry(long.class, "a whole number"), Map.entry(Long.class, "a whole number"),
            Map.entry(double.class, "a number"), Map.entry(Double.class, "a number"),
            Map.entry(float.class, "a number"), Map.entry(Float.class, "a number"),
            Map.entry(BigDecimal.class, "a number"),
            Map.entry(boolean.class, "true or false"), Map.entry(Boolean.class, "true or false"),
            Map.entry(String.class, "text"));

    /** The kind of value a type takes, as a person reading the error would name it. */
    private static String describe(Class<?> type) {
        if (type.isEnum()) {
            return "one of " + Arrays.stream(type.getEnumConstants()).map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        if (Collection.class.isAssignableFrom(type)) {
            return "a list";
        }
        return KINDS.getOrDefault(type, "a mapping");
    }
}
