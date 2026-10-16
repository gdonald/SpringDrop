package dev.springdrop.kernel.config.sync;

import java.util.List;

/** The config schemas one part of the site defines, so its config is type-checked on import. */
public interface ConfigSchemaProvider {

    List<ConfigSchema> schemas();
}
