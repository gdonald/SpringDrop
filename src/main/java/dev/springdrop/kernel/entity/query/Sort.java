package dev.springdrop.kernel.entity.query;

import java.util.List;

/**
 * One sort of an entity query: the property to order by and the direction, or
 * for a sort by position, the values in the order results come in. A result
 * whose value is not among them comes after those that are.
 */
public record Sort(String property, boolean ascending, List<Object> positions) {

    public Sort {
        positions = (positions == null) ? null : List.copyOf(positions);
    }

    public static Sort ascending(String property) {
        return new Sort(property, true, null);
    }

    public static Sort descending(String property) {
        return new Sort(property, false, null);
    }

    public static Sort byPosition(String property, List<?> values) {
        return new Sort(property, true, List.copyOf(values));
    }
}
