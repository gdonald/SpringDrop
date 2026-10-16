package dev.springdrop.kernel.file;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The scheme an upload is kept in: the one its field's storage names, or else the site's default. */
@Component
public class UploadSchemes {

    private final ConfigStore config;

    public UploadSchemes(ConfigStore config) {
        this.config = config;
    }

    public String defaultScheme() {
        return config.read(FileSystemSettings.CONFIG_NAME, FileSystemSettings.class, FileSystemSettings.DEFAULTS)
                .defaultScheme();
    }

    public String schemeFor(Map<String, Object> storageSettings) {
        Object named = storageSettings.get(FileItem.URI_SCHEME);
        return named == null ? defaultScheme() : named.toString();
    }
}
