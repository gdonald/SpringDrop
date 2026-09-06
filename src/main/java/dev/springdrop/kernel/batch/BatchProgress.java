package dev.springdrop.kernel.batch;

import java.util.List;

/**
 * How far a batch has come: how many operations there are, how many are done,
 * what went wrong along the way, and whether it has finished.
 */
public record BatchProgress(
        String batchId,
        int total,
        int processed,
        List<String> errors,
        boolean finished) {

    public BatchProgress {
        errors = List.copyOf(errors);
    }

    /** How far along the batch is, as a whole percentage. */
    public int percentage() {
        return (total == 0) ? 100 : Math.round(processed * 100f / total);
    }

    public int remaining() {
        return total - processed;
    }
}
