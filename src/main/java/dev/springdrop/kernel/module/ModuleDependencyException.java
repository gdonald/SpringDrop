package dev.springdrop.kernel.module;

/**
 * Raised when module descriptors cannot be loaded or their dependency graph is
 * invalid (a missing dependency or a cycle).
 */
public class ModuleDependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModuleDependencyException(String message) {
        super(message);
    }

    public ModuleDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
