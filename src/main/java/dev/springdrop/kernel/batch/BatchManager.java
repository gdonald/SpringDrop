package dev.springdrop.kernel.batch;

import dev.springdrop.kernel.state.StateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Runs long work a chunk at a time, so no single request has to carry all of it.
 * A batch is stored when it starts and picked up again on each request, which
 * keeps its progress across requests and lets a browser watch it. An operation
 * that throws is recorded against the batch and the rest carry on.
 */
@Component
public class BatchManager {

    static final String STATE_COLLECTION = "batch";

    private static final String DEFINITION_SUFFIX = ".definition";

    private static final String PROGRESS_SUFFIX = ".progress";

    private final StateService stateService;
    private final Map<String, BatchOperationHandler> handlers;

    public BatchManager(StateService stateService, List<BatchOperationHandler> handlers) {
        this.stateService = stateService;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(handler -> handler.id(), Function.identity()));
    }

    /** Stores the batch so it can be run a chunk at a time, and reports where it stands. */
    public BatchProgress start(BatchDefinition definition) {
        stateService.set(STATE_COLLECTION, definition.batchId() + DEFINITION_SUFFIX, definition);
        BatchProgress progress = new BatchProgress(
                definition.batchId(), definition.operations().size(), 0, List.of(), false);
        return save(progress);
    }

    public Optional<BatchProgress> progress(String batchId) {
        return stateService.get(STATE_COLLECTION, batchId + PROGRESS_SUFFIX, BatchProgress.class);
    }

    public Optional<BatchDefinition> definition(String batchId) {
        return stateService.get(STATE_COLLECTION, batchId + DEFINITION_SUFFIX, BatchDefinition.class);
    }

    /**
     * Carries out the next chunk of a batch and reports where it now stands. A
     * batch that has finished is left alone, so a browser that polls once more
     * does not run anything again.
     */
    public BatchProgress processChunk(String batchId) {
        BatchDefinition definition = definition(batchId).orElseThrow(
                () -> new IllegalArgumentException("No batch stored with id '" + batchId + "'"));
        BatchProgress progress = progress(batchId).orElseThrow(
                () -> new IllegalArgumentException("No progress stored for batch '" + batchId + "'"));

        if (progress.finished()) {
            return progress;
        }

        List<String> errors = new ArrayList<>(progress.errors());
        int processed = progress.processed();
        int chunkEnd = Math.min(processed + definition.chunkSize(), definition.operations().size());
        for (int index = processed; index < chunkEnd; index++) {
            run(definition.operations().get(index), errors);
            processed++;
        }

        return save(new BatchProgress(batchId, definition.operations().size(), processed, errors,
                processed >= definition.operations().size()));
    }

    /** Runs the batch to its end in one go, for work that need not be watched. */
    public BatchProgress processAll(String batchId) {
        BatchProgress progress = processChunk(batchId);
        while (!progress.finished()) {
            progress = processChunk(batchId);
        }
        return progress;
    }

    public void forget(String batchId) {
        stateService.remove(STATE_COLLECTION, batchId + DEFINITION_SUFFIX);
        stateService.remove(STATE_COLLECTION, batchId + PROGRESS_SUFFIX);
    }

    private void run(BatchOperationSpec operation, List<String> errors) {
        BatchOperationHandler handler = handlers.get(operation.handlerId());
        if (handler == null) {
            errors.add("No batch handler with id '" + operation.handlerId() + "'");
            return;
        }
        try {
            handler.run(operation.payload());
        } catch (RuntimeException failure) {
            errors.add(operation.handlerId() + ": " + failure.getMessage());
        }
    }

    private BatchProgress save(BatchProgress progress) {
        stateService.set(STATE_COLLECTION, progress.batchId() + PROGRESS_SUFFIX, progress);
        return progress;
    }
}
