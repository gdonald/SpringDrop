package dev.springdrop.kernel.views.handlers;

import java.util.Optional;

/**
 * Reads text a handler is given as the kind of value its property holds:
 * {@code text} as it is, {@code number} as a whole number, and {@code boolean}
 * as true or false. Text that is not a value of the kind reads as nothing.
 */
final class TypedValues {

    static final String TYPE = "type";

    static final String TEXT = "text";

    static final String NUMBER = "number";

    static final String BOOLEAN = "boolean";

    private TypedValues() {
    }

    static Optional<Object> typed(String type, String value) {
        return switch (type) {
            case NUMBER -> value.matches("-?\\d{1,18}") ? Optional.of(Long.valueOf(value)) : Optional.empty();
            case BOOLEAN -> value.equals("true") || value.equals("false")
                    ? Optional.of(Boolean.valueOf(value)) : Optional.empty();
            default -> Optional.of(value);
        };
    }
}
