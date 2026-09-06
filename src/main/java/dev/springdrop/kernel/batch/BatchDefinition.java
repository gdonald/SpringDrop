package dev.springdrop.kernel.batch;

import java.util.List;

/**
 * A batch waiting to run: the operations it holds, how many to carry out per
 * request, and where the person goes once it has finished.
 */
public record BatchDefinition(
        String batchId,
        List<BatchOperationSpec> operations,
        int chunkSize,
        String finishedPath) {

    public static final int DEFAULT_CHUNK_SIZE = 10;

    public BatchDefinition {
        operations = List.copyOf(operations);
    }

    public static BatchDefinition of(String batchId, List<BatchOperationSpec> operations) {
        return new BatchDefinition(batchId, operations, DEFAULT_CHUNK_SIZE, "/");
    }

    public BatchDefinition inChunksOf(int newChunkSize) {
        return new BatchDefinition(batchId, operations, newChunkSize, finishedPath);
    }

    public BatchDefinition finishingAt(String path) {
        return new BatchDefinition(batchId, operations, chunkSize, path);
    }
}
