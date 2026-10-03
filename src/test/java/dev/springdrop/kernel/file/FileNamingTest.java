package dev.springdrop.kernel.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileNamingTest {

    @Test
    void aNameKeepsLettersDigitsDotsDashesAndUnderscores() {
        assertThat(FileService.safeName("Opening hours (May).pdf")).isEqualTo("Opening_hours_May_.pdf");
    }

    @Test
    void aNameLosesTheDirectoriesItWasSentWith() {
        assertThat(FileService.safeName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileService.safeName("C:\\Users\\edith\\notes.txt")).isEqualTo("notes.txt");
    }

    @Test
    void aNameThatWouldRunOrRenderAsAPageIsServedAsText() {
        assertThat(FileService.safeName("shell.php")).isEqualTo("shell.php.txt");
        assertThat(FileService.safeName("page.HTML")).isEqualTo("page.HTML.txt");
    }

    @Test
    void aNameWithNothingSafeLeftIsCalledFile() {
        assertThat(FileService.safeName("...")).isEqualTo("file");
        assertThat(FileService.safeName("README")).isEqualTo("README");
    }

    @Test
    void aTakenNameGetsANumberBeforeItsExtension() {
        assertThat(FileService.numbered("hours.pdf", 2)).isEqualTo("hours_2.pdf");
        assertThat(FileService.numbered("README", 1)).isEqualTo("README_1");
    }

    @Test
    void aPathOutsideTheSchemesDirectoryIsRefused() {
        LocalDiskScheme scheme = new LocalDiskScheme("public", Path.of("files", "public"), "/files");

        assertThatThrownBy(() -> scheme.resolve("../private/secret.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheme.resolve("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(scheme.directUrl("2026-10/hours.pdf")).contains("/files/2026-10/hours.pdf");
        assertThat(new LocalDiskScheme("private", Path.of("files", "private"), null).directUrl("a.txt")).isEmpty();
    }
}
