package dev.springdrop.kernel.permission;

import java.util.List;

/**
 * Declares the permissions one module offers. Core reads these from each
 * module's {@code permissions.yml}; a module that would rather build its list in
 * code registers a provider bean instead.
 */
@FunctionalInterface
public interface PermissionProvider {

    List<PermissionDefinition> permissions();
}
