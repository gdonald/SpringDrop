package dev.springdrop.kernel.install;

import dev.springdrop.kernel.file.FileSystemProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * What the installer checks before it goes ahead: a PostgreSQL database of
 * version {@value #MINIMUM_POSTGRES_MAJOR} or later, every core migration
 * applied, and file directories the site can write to.
 */
@Component
public class InstallRequirements {

    public static final int MINIMUM_POSTGRES_MAJOR = 13;

    private final DSLContext dsl;
    private final Flyway flyway;
    private final FileSystemProperties files;

    public InstallRequirements(DSLContext dsl, Flyway flyway, FileSystemProperties files) {
        this.dsl = dsl;
        this.flyway = flyway;
        this.files = files;
    }

    public List<InstallRequirement> check() {
        return List.of(database(), migrations(), directory("Public files directory", files.publicPath()),
                directory("Private files directory", files.privatePath()));
    }

    public boolean allMet() {
        return check().stream().allMatch(InstallRequirement::met);
    }

    private InstallRequirement database() {
        int version = Integer.parseInt(String.valueOf(dsl.fetchValue("show server_version_num")));
        return new InstallRequirement("Database", "PostgreSQL " + (version / 10000) + "." + (version % 10000),
                version / 10000 >= MINIMUM_POSTGRES_MAJOR,
                "PostgreSQL " + MINIMUM_POSTGRES_MAJOR + " or later is needed.");
    }

    private InstallRequirement migrations() {
        int applied = flyway.info().applied().length;
        int pending = flyway.info().pending().length;
        return new InstallRequirement("Database schema", applied + " migrations applied, " + pending + " pending",
                pending == 0, "Restart the site so it applies the pending migrations.");
    }

    InstallRequirement directory(String title, String path) {
        Path directory = Path.of(path).toAbsolutePath();
        boolean writable;
        try {
            Files.createDirectories(directory);
            writable = Files.isWritable(directory);
        } catch (IOException unwritable) {
            writable = false;
        }
        return new InstallRequirement(title, directory.toString(), writable,
                "The site needs to create and write to this directory.");
    }
}
