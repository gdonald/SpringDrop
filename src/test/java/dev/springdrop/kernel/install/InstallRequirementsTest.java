package dev.springdrop.kernel.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.file.FileSystemProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallRequirementsTest {

    @TempDir
    Path directory;

    private InstallRequirements requirements(String serverVersion, int pending) {
        DSLContext dsl = mock(DSLContext.class);
        when(dsl.fetchValue("show server_version_num")).thenReturn(serverVersion);
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.applied()).thenReturn(new MigrationInfo[] {mock(MigrationInfo.class)});
        when(info.pending()).thenReturn(new MigrationInfo[pending]);
        return new InstallRequirements(dsl, flyway, new FileSystemProperties(
                directory.resolve("public").toString(), directory.resolve("private").toString()));
    }

    @Test
    void aCurrentDatabaseWithEveryMigrationAndWritableDirectoriesMeetsThemAll() {
        InstallRequirements requirements = requirements("170002", 0);

        assertThat(requirements.check()).extracting(InstallRequirement::found)
                .startsWith("PostgreSQL 17.2", "1 migrations applied, 0 pending");
        assertThat(requirements.allMet()).isTrue();
    }

    @Test
    void anOldDatabaseOrPendingMigrationsAreNotMet() {
        assertThat(requirements("120009", 0).check().get(0).met()).isFalse();
        assertThat(requirements("170002", 2).check().get(1).met()).isFalse();
        assertThat(requirements("170002", 2).allMet()).isFalse();
    }

    @Test
    void aDirectoryThatCannotBeMadeIsNotMet() throws Exception {
        Path blocked = Files.writeString(directory.resolve("blocked"), "a file, not a directory");

        InstallRequirement files = requirements("170002", 0).directory("Public files directory",
                blocked.resolve("public").toString());

        assertThat(files.met()).isFalse();
        assertThat(files.help()).isEqualTo("The site needs to create and write to this directory.");
    }
}
