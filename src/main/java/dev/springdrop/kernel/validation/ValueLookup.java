package dev.springdrop.kernel.validation;

/**
 * The storage questions a constraint may need answered: whether a value is
 * already taken, and whether a referenced object exists. Entity storage supplies
 * a real implementation; {@link #permissive()} is used when there is no storage
 * to consult, such as validating an object that is not attached to one.
 */
public interface ValueLookup {

    boolean valueInUse(String propertyPath, Object value);

    boolean referenceExists(String target, Object id);

    static ValueLookup permissive() {
        return new ValueLookup() {
            @Override
            public boolean valueInUse(String propertyPath, Object value) {
                return false;
            }

            @Override
            public boolean referenceExists(String target, Object id) {
                return true;
            }
        };
    }
}
