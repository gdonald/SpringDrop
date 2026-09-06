package dev.springdrop.kernel.entity.query;

import java.util.List;

/**
 * A filter on an entity query: either a comparison of one property, or a group
 * of conditions joined by AND or OR. Groups nest, so a query can ask for a value
 * in one field together with either of two values in another.
 */
public sealed interface Condition {

    /** Compares one property, a base key or a field name, against a value. */
    record Comparison(String property, Operator operator, Object value) implements Condition {
    }

    /** Several conditions joined by a {@link Conjunction}. */
    record Group(Conjunction conjunction, List<Condition> conditions) implements Condition {

        public Group {
            conditions = List.copyOf(conditions);
        }
    }

    enum Operator {
        EQUAL,
        NOT_EQUAL,
        GREATER_THAN,
        LESS_THAN,
        IN,
        CONTAINS
    }

    enum Conjunction {
        AND,
        OR
    }

    static Condition equal(String property, Object value) {
        return new Comparison(property, Operator.EQUAL, value);
    }

    static Condition notEqual(String property, Object value) {
        return new Comparison(property, Operator.NOT_EQUAL, value);
    }

    static Condition greaterThan(String property, Object value) {
        return new Comparison(property, Operator.GREATER_THAN, value);
    }

    static Condition lessThan(String property, Object value) {
        return new Comparison(property, Operator.LESS_THAN, value);
    }

    static Condition in(String property, List<?> values) {
        return new Comparison(property, Operator.IN, values);
    }

    static Condition contains(String property, String fragment) {
        return new Comparison(property, Operator.CONTAINS, fragment);
    }

    static Condition anyOf(Condition... conditions) {
        return new Group(Conjunction.OR, List.of(conditions));
    }

    static Condition allOf(Condition... conditions) {
        return new Group(Conjunction.AND, List.of(conditions));
    }
}
