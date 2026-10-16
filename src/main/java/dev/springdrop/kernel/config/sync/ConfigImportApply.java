package dev.springdrop.kernel.config.sync;

import dev.springdrop.kernel.batch.BatchOperationHandler;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Applies every change in the sync directory, all of them or none. */
@Component
public class ConfigImportApply implements BatchOperationHandler {

    private final ConfigSync sync;

    public ConfigImportApply(ConfigSync sync) {
        this.sync = sync;
    }

    @Override
    public String id() {
        return ConfigImportBatch.APPLY;
    }

    @Override
    public void run(Map<String, Object> payload) {
        sync.apply(Path.of((String) payload.get(ConfigImportBatch.DIRECTORY)));
    }
}
