package dev.springdrop.kernel.batch;

import java.util.Map;

/**
 * Carries out one kind of batch operation. A handler is a bean named by its id,
 * so a batch can be picked up again in a later request and still know what to
 * run. Throwing marks that one operation as failed; the batch carries on.
 */
public interface BatchOperationHandler {

    String id();

    void run(Map<String, Object> payload);
}
