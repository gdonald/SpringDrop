package dev.springdrop.kernel.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.cron.CronRunner;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.support.AbstractIntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FileIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] HOURS = "Open daily from nine.".getBytes(StandardCharsets.UTF_8);

    /** Old enough that every temporary file counts as stale. */
    private static final Duration ALREADY_STALE = Duration.ofMinutes(-1);

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private CronRunner cron;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void noFilesLeft() {
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
    }

    private ManagedFile stored(String name, String scheme) throws IOException {
        return files.store(new ByteArrayInputStream(HOURS), name, HOURS.length, scheme, 7L);
    }

    @Test
    void anUploadedFileIsStoredUnderItsSchemeAndLoadsWithItsMetadata() throws IOException {
        ManagedFile file = stored("Opening hours.txt", FileSchemes.PRIVATE);

        ManagedFile loaded = files.find(file.id()).orElseThrow();

        assertThat(loaded.uri()).startsWith("private://").endsWith("/Opening_hours.txt");
        assertThat(loaded.filename()).isEqualTo("Opening_hours.txt");
        assertThat(loaded.mime()).isEqualTo("text/plain");
        assertThat(loaded.size()).isEqualTo(HOURS.length);
        assertThat(loaded.owner()).isEqualTo(7L);
        assertThat(loaded.permanent()).isFalse();
        assertThat(Files.exists(FILES.resolve("private").resolve(loaded.path()))).isTrue();
        try (InputStream content = files.read(loaded)) {
            assertThat(content.readAllBytes()).isEqualTo(HOURS);
        }
        assertThat(files.url(loaded)).isEqualTo(FileService.PRIVATE_URL_PREFIX + "/" + loaded.path());
        assertThat(files.findByUri(loaded.uri())).map(ManagedFile::id).contains(loaded.id());
    }

    @Test
    void aPublicFileIsServedAsItIs() throws Exception {
        ManagedFile file = stored("hours.txt", FileSchemes.PUBLIC);

        mockMvc.perform(get(files.url(file))).andExpect(status().isOk()).andExpect(content().bytes(HOURS));
    }

    @Test
    void aSecondFileOfTheSameNameIsNumbered() throws IOException {
        ManagedFile first = stored("hours.txt", FileSchemes.PUBLIC);
        ManagedFile second = stored("hours.txt", FileSchemes.PUBLIC);

        assertThat(second.path()).isNotEqualTo(first.path()).endsWith("/hours_1.txt");
    }

    @Test
    void aFileOfAnUnknownKindIsStoredAsBytes() throws IOException {
        assertThat(stored("data.unknownkind", FileSchemes.PUBLIC).mime()).isEqualTo("application/octet-stream");
    }

    @Test
    void aSchemeTheSiteDoesNotHaveIsRefused() {
        assertThatThrownBy(() -> stored("hours.txt", "s3")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingAFileRemovesItsContent() throws IOException {
        ManagedFile file = stored("hours.txt", FileSchemes.PUBLIC);

        files.delete(file.id());

        assertThat(files.find(file.id())).isEmpty();
        assertThat(Files.exists(FILES.resolve("public").resolve(file.path()))).isFalse();
        files.delete(file.id());
    }

    @Test
    void aFileWhoseContentCannotBeRemovedIsKept() throws IOException {
        ManagedFile file = stored("hours.txt", FileSchemes.PUBLIC);
        Path onDisk = FILES.resolve("public").resolve(file.path());
        Files.delete(onDisk);
        Files.createDirectories(onDisk.resolve("inside"));

        assertThatThrownBy(() -> files.delete(file.id())).isInstanceOf(UncheckedIOException.class);
        assertThat(files.find(file.id())).isPresent();

        Files.delete(onDisk.resolve("inside"));
    }

    @Test
    void usingAFileMakesItPermanentAndNoLongerUsingItMakesItTemporaryAgain() throws IOException {
        ManagedFile file = stored("hours.txt", FileSchemes.PUBLIC);

        usage.add(file.id(), "file", "node", 4L);
        usage.add(file.id(), "file", "node", 4L);
        usage.add(file.id(), "file", "block_content", 2L);
        assertThat(files.find(file.id())).map(ManagedFile::permanent).contains(true);
        assertThat(usage.total(file.id())).isEqualTo(3);
        assertThat(usage.filesUsedBy("file", "node", 4L)).containsExactly(file.id());

        usage.remove(file.id(), "file", "node", 4L, false);
        assertThat(usage.usages(file.id())).extracting(FileUsage::count).containsExactly(1, 1);
        usage.remove(file.id(), "file", "node", 4L, false);
        usage.remove(file.id(), "file", "block_content", 2L, true);

        assertThat(usage.total(file.id())).isZero();
        assertThat(files.find(file.id())).map(ManagedFile::permanent).contains(false);
    }

    @Test
    void cronCollectsAnUnusedTemporaryFileAndKeepsOneInUse() throws IOException {
        ManagedFile unused = stored("draft.txt", FileSchemes.PUBLIC);
        ManagedFile used = stored("hours.txt", FileSchemes.PUBLIC);
        usage.add(used.id(), "file", "node", 4L);
        ManagedFile markedTemporary = stored("kept.txt", FileSchemes.PUBLIC);
        usage.add(markedTemporary.id(), "file", "node", 5L);
        files.setPermanent(markedTemporary.id(), false);

        new TemporaryFileCollector(files, usage, ALREADY_STALE).run();

        assertThat(files.find(unused.id())).isEmpty();
        assertThat(files.find(used.id())).isPresent();
        assertThat(files.find(markedTemporary.id())).isPresent();
    }

    @Test
    void aFreshTemporaryFileWaitsOutTheGracePeriod() throws IOException {
        ManagedFile fresh = stored("draft.txt", FileSchemes.PUBLIC);

        cron.run();

        assertThat(files.find(fresh.id())).isPresent();
        assertThat(cron.jobIds()).contains("file.temporary");
    }
}
