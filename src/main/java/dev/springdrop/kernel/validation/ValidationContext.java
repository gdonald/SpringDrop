package dev.springdrop.kernel.validation;

/**
 * What a constraint may consult beyond the value it is checking: the whole
 * object under validation, and the storage lookup behind it.
 */
public record ValidationContext(Object subject, ValueLookup lookup) {

    public static ValidationContext of(Object subject) {
        return new ValidationContext(subject, ValueLookup.permissive());
    }
}
