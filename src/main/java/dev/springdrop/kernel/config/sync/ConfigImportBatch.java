package dev.springdrop.kernel.config.sync;

import dev.springdrop.kernel.batch.BatchDefinition;
import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchOperationSpec;
import dev.springdrop.kernel.batch.BatchProgress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Runs an import through the Batch API: one operation reads each changed
 * object's file, so an unreadable file is reported against its own name, and
 * a last operation applies every change in one transaction.
 */
@Component
public class ConfigImportBatch {

    public static final String CHECK = "config_import.check";

    public static final String APPLY = "config_import.apply";

    static final String DIRECTORY = "directory";

    static final String NAME = "name";

    private final BatchManager batches;
    private final ConfigSync sync;

    public ConfigImportBatch(BatchManager batches, ConfigSync sync) {
        this.batches = batches;
        this.sync = sync;
    }

    public BatchProgress start(Path directory, String finishedPath) {
        List<BatchOperationSpec> operations = new ArrayList<>();
        for (ConfigChange change : sync.changes(directory)) {
            operations.add(BatchOperationSpec.of(CHECK, Map.of(DIRECTORY, directory.toString(), NAME,
                    change.name())));
        }
        operations.add(BatchOperationSpec.of(APPLY, Map.of(DIRECTORY, directory.toString())));
        return batches.start(BatchDefinition.of("config-import-" + UUID.randomUUID(), operations)
                .finishingAt(finishedPath));
    }
}
