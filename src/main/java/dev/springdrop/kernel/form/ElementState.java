package dev.springdrop.kernel.form;

/**
 * A condition an element reacts to: what happens, which other element decides
 * it, and the value that element must hold. One format serves both sides, so the
 * browser script and the server read the same rule and reach the same answer.
 */
public record ElementState(String condition, String dependsOn, String equalsValue) {

    /** The element is shown only while the condition holds. */
    public static final String VISIBLE = "visible";

    /** The element must be filled in only while the condition holds. */
    public static final String REQUIRED = "required";

    /** The element is disabled while the condition holds. */
    public static final String DISABLED = "disabled";

    /** The wire format both the renderer and the script read: {@code element:value}. */
    public String selector() {
        return dependsOn + ":" + equalsValue;
    }

    public boolean holds(Object submittedValue) {
        return equalsValue.equals(String.valueOf(submittedValue));
    }
}
