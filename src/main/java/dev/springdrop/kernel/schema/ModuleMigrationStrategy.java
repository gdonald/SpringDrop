package dev.springdrop.kernel.schema;

import dev.springdrop.kernel.module.ModuleInfo;
import dev.springdrop.kernel.module.ModuleRegistry;
import java.io.IOException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Applies the kernel migrations in {@code db/migration/core} first, then each
 * module's own migrations from {@code db/migration/<module>} in module dependency
 * order, so a module's tables can reference tables its dependencies created.
 * Each module keeps its own schema history table, which lets modules version
 * their migrations independently of core and of each other.
 */
@Component
public class ModuleMigrationStrategy implements FlywayMigrationStrategy {

    static final String LOCATION_PREFIX = "classpath:db/migration/";

    private static final String HISTORY_TABLE_PREFIX = "flyway_schema_history_";

    private final ModuleRegistry moduleRegistry;
    private final DataSource dataSource;
    private final ResourcePatternResolver resourcePatternResolver;

    public ModuleMigrationStrategy(
            ModuleRegistry moduleRegistry,
            DataSource dataSource,
            ResourcePatternResolver resourcePatternResolver) {
        this.moduleRegistry = moduleRegistry;
        this.dataSource = dataSource;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public void migrate(Flyway core) {
        core.migrate();
        for (ModuleInfo module : moduleRegistry.installOrder()) {
            if (hasMigrations(module.name())) {
                migrateModule(module.name());
            }
        }
    }

    private void migrateModule(String moduleName) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION_PREFIX + moduleName)
                .table(HISTORY_TABLE_PREFIX + moduleName)
                // The schema already holds the core tables, so a module's first
                // migration run baselines at version 0 to create its history
                // table without skipping the module's own V1.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    private boolean hasMigrations(String moduleName) {
        try {
            return resourcePatternResolver
                    .getResources("classpath*:db/migration/" + moduleName + "/*.sql").length > 0;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan migrations for module '" + moduleName + "'", e);
        }
    }
}
