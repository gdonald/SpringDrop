package dev.springdrop.kernel.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown when a route's entity parameter cannot be loaded, producing a themed
 * 404. Raised by the entity argument resolver during parameter upcasting.
 */
public class EntityNotFoundException extends ResponseStatusException {

    public EntityNotFoundException(String entityType, String id) {
        super(HttpStatus.NOT_FOUND, entityType + " '" + id + "' was not found");
    }
}
