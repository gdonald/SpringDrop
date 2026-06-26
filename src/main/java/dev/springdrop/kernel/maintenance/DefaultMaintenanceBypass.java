package dev.springdrop.kernel.maintenance;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Default bypass that allows no one through. Replaced by a permission-backed
 * implementation once the access system is in place.
 */
@Component
public class DefaultMaintenanceBypass implements MaintenanceBypass {

    @Override
    public boolean isAllowed(HttpServletRequest request) {
        return false;
    }
}
