package dev.springdrop.kernel.maintenance;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides whether a request may bypass maintenance mode. The default
 * implementation lets no one through; the access system replaces it with a
 * permission-backed check once roles and permissions exist.
 */
public interface MaintenanceBypass {

    boolean isAllowed(HttpServletRequest request);
}
