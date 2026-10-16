package dev.springdrop.kernel.jsonapi;

import java.util.List;

/**
 * A collection filter as the {@code filter} parameters describe it: a
 * comparison of one path against a value, or a group of filters joined by
 * {@code AND} or {@code OR}.
 */
public sealed interface JsonApiFilter {

    String EQUAL = "=";

    String NOT_EQUAL = "<>";

    String GREATER_THAN = ">";

    String LESS_THAN = "<";

    String IN = "IN";

    String CONTAINS = "CONTAINS";

    List<String> OPERATORS = List.of(EQUAL, NOT_EQUAL, GREATER_THAN, LESS_THAN, IN, CONTAINS);

    String AND = "AND";

    String OR = "OR";

    /** A comparison. The value is a list for {@code IN} and text for every other operator. */
    record Match(String path, String operator, Object value) implements JsonApiFilter {
    }

    record Group(String conjunction, List<JsonApiFilter> members) implements JsonApiFilter {

        public Group {
            members = List.copyOf(members);
        }
    }
}
