package dev.springdrop.kernel.batch;

import java.util.Map;

/**
 * One piece of work in a batch: which handler carries it out, and what it is
 * given to work on. Operations are stored between requests, so they name their
 * handler rather than carrying code.
 */
public record BatchOperationSpec(String handlerId, Map<String, Object> payload) {

    public BatchOperationSpec {
        payload = Map.copyOf(payload);
    }

    public static BatchOperationSpec of(String handlerId, Map<String, Object> payload) {
        return new BatchOperationSpec(handlerId, payload);
    }
}
