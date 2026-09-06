package dev.springdrop.kernel.field.formatter;

/**
 * Renders one value of a field for reading. A formatter is a plugin registered
 * with {@code @SpringDropPlugin(type = FieldFormatter.class)}, so a module adds
 * one without core knowing about it. Multi-value handling is done around it: it
 * is asked for one value at a time.
 */
public interface FieldFormatter {

    String id();

    /** The markup for one value, already escaped where it needs to be. */
    String render(FormatterContext context, Object value);
}
