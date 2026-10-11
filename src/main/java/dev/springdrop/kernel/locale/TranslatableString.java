package dev.springdrop.kernel.locale;

/**
 * One interface string and its translation into one language: the English
 * source, the context telling apart two uses of the same words, and the
 * translation, empty when there is none. A plural string's source and
 * translation hold their forms joined by {@link Translator#PLURAL_DELIMITER}.
 */
public record TranslatableString(long id, String source, String context, String translation) {
}
