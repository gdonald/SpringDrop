package dev.springdrop.kernel.entity.query;

/**
 * One sort of an entity query: the property to order by and the direction.
 */
public record Sort(String property, boolean ascending) {

    public static Sort ascending(String property) {
        return new Sort(property, true);
    }

    public static Sort descending(String property) {
        return new Sort(property, false);
    }
}
