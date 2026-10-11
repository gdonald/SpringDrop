package dev.springdrop.kernel.rest;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.BundleFieldMap;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Which values a REST request may write, and each one as its column takes it.
 * A request writes the bundle's configurable fields, passed as they are, and
 * base fields holding text, numbers, or yes-or-no values, read from the text
 * or JSON value given. The owner, dates, and revision bookkeeping are kept by
 * the site and cannot be written.
 */
@Component
public class RestFieldValues {

    static final Set<String> READ_ONLY = Set.of(BaseFieldDefinition.OWNER, BaseFieldDefinition.CREATED,
            BaseFieldDefinition.CHANGED);

    private final BundleFieldMap bundleFields;

    public RestFieldValues(BundleFieldMap bundleFields) {
        this.bundleFields = bundleFields;
    }

    /** The values as the entity holds them, or the first problem with one, as an error message. */
    public Result writable(EntityType type, String bundle, Map<String, Object> submitted) {
        List<String> configurable = bundleFields.fieldNames(type.id(),
                type.bundleEntityType() == null ? type.id() : bundle);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : submitted.entrySet()) {
            String name = entry.getKey();
            if (configurable.contains(name)) {
                values.put(name, entry.getValue());
                continue;
            }
            Optional<BaseFieldDefinition> base = type.baseFields().stream()
                    .filter(field -> field.name().equals(name)).findFirst();
            if (base.isEmpty() || READ_ONLY.contains(name) || name.startsWith("revision_")) {
                return Result.refused("The field " + name + " cannot be written.");
            }
            Optional<Object> coerced = coerce(base.get().type(), entry.getValue());
            if (coerced.isEmpty()) {
                return Result.refused("The value of " + name + " is not a " + describe(base.get().type()) + ".");
            }
            values.put(name, coerced.get());
        }
        return new Result(values, null);
    }

    /** The values to write, or the reason they were refused. */
    public record Result(Map<String, Object> values, String error) {

        static Result refused(String error) {
            return new Result(Map.of(), error);
        }
    }

    private static Optional<Object> coerce(ColumnType type, Object value) {
        String text = String.valueOf(value);
        return switch (type) {
            case BOOLEAN -> text.equals("true") || text.equals("false")
                    ? Optional.of(Boolean.parseBoolean(text)) : Optional.empty();
            case BIGINT -> text.matches("-?\\d{1,18}") ? Optional.of(Long.parseLong(text)) : Optional.empty();
            case INTEGER -> text.matches("-?\\d{1,9}") ? Optional.of(Integer.parseInt(text)) : Optional.empty();
            case TEXT, VARCHAR -> Optional.of(text);
            default -> Optional.empty();
        };
    }

    private static String describe(ColumnType type) {
        return switch (type) {
            case BOOLEAN -> "true or false value";
            case BIGINT, INTEGER -> "whole number";
            default -> "value this field takes";
        };
    }
}
