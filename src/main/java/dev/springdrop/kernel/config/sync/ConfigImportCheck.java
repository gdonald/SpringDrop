package dev.springdrop.kernel.config.sync;

import dev.springdrop.kernel.batch.BatchOperationHandler;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reads one changed object's file, failing the operation when the file cannot be read. */
@Component
public class ConfigImportCheck implements BatchOperationHandler {

    private final ConfigFiles files;

    public ConfigImportCheck(ConfigFiles files) {
        this.files = files;
    }

    @Override
    public String id() {
        return ConfigImportBatch.CHECK;
    }

    @Override
    public void run(Map<String, Object> payload) {
        files.read(Path.of((String) payload.get(ConfigImportBatch.DIRECTORY)), (String) payload.get(
                ConfigImportBatch.NAME));
    }
}
