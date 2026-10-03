package dev.springdrop.kernel.file;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class FileFieldTypesTest {

    private static final FileFieldType FILE = new FileFieldType();

    private static final ImageFieldType IMAGE = new ImageFieldType();

    private static String name(String setting) {
        return FileItem.SETTINGS_PREFIX + setting;
    }

    @Test
    void aFileFieldKeepsUploadsInPublicFilesAndAllowsTextFilesByDefault() {
        assertThat(FILE.id()).isEqualTo("file");
        assertThat(FILE.defaultStorageSettings()).containsEntry(FileItem.URI_SCHEME, FileSchemes.PUBLIC);
        assertThat(FILE.defaultInstanceSettings()).containsEntry(FileItem.FILE_EXTENSIONS, "txt");
        assertThat(FILE.defaultWidget()).isEqualTo("file_generic");
        assertThat(FILE.defaultFormatter()).isEqualTo(FileFormatter.ID);
        assertThat(FILE.properties()).extracting(property -> property.name())
                .containsExactly(FileItem.TARGET_ID, FileItem.DISPLAY, FileItem.DESCRIPTION);
    }

    @Test
    void anImageFieldAllowsTheImageKindsTheSiteCanReadAndAsksForAlternativeText() {
        assertThat(IMAGE.id()).isEqualTo("image");
        assertThat(IMAGE.defaultStorageSettings()).containsEntry(FileItem.URI_SCHEME, FileSchemes.PUBLIC);
        assertThat(IMAGE.defaultInstanceSettings())
                .containsEntry(FileItem.FILE_EXTENSIONS, "png gif jpg jpeg")
                .containsEntry(FileItem.ALT_FIELD_REQUIRED, true);
        assertThat(IMAGE.defaultWidget()).isEqualTo("image_image");
        assertThat(IMAGE.defaultFormatter()).isEqualTo(ImageFormatter.ID);
    }

    @Test
    void anImageValueHoldsItsFileAlternativeTextTitleAndSize() {
        assertThat(IMAGE.properties()).extracting(property -> property.name()).containsExactly(
                FileItem.TARGET_ID, FileItem.ALT, FileItem.TITLE, FileItem.WIDTH, FileItem.HEIGHT);
    }

    @Test
    void anImageFieldThatAsksForAlternativeTextWithoutRequiringItLetsItBeBlank() {
        FieldStorageConfig storage = new FieldStorageConfig("photo", "node", ImageFieldType.ID, 1, Map.of());
        FieldInstanceConfig optional = FieldInstanceConfig.of("photo", "node", "page", "Photo").withSettings(Map.of(
                FileItem.ALT_FIELD, true, FileItem.ALT_FIELD_REQUIRED, false));

        assertThat(IMAGE.defaultConstraints(storage, optional).getFirst().options())
                .containsEntry(FileItem.ALT_FIELD_REQUIRED, false);
    }

    @Test
    void anImageIsOutsideItsBoundsWhenEitherSideIs() {
        ImageSize image = new ImageSize(100, 50);

        assertThat(List.of(image.smallerThan(101, 10), image.smallerThan(10, 51), image.smallerThan(100, 50)))
                .containsExactly(true, true, false);
        assertThat(List.of(image.largerThan(99, 60), image.largerThan(200, 49), image.largerThan(100, 50)))
                .containsExactly(true, true, false);
    }

    @Test
    void theFormattersAreFoundByTheIdsTheFieldTypesName() {
        assertThat(new FileFormatter(null).id()).isEqualTo(FILE.defaultFormatter());
        assertThat(new ImageFormatter(null, null, null).id()).isEqualTo(IMAGE.defaultFormatter());
    }

    @Test
    void theWidgetsAreFoundByTheIdsTheFieldTypesName() {
        assertThat(new FileGenericWidget(null, DataSize.ofMegabytes(1)).id()).isEqualTo(FILE.defaultWidget());
        assertThat(new ImageWidget(null, DataSize.ofMegabytes(1)).id()).isEqualTo(IMAGE.defaultWidget());
    }

    @Test
    void anImageFieldOnlyRequiresAlternativeTextWhenItAsksForIt() {
        FieldStorageConfig storage = new FieldStorageConfig("photo", "node", ImageFieldType.ID, 1, Map.of());
        FieldInstanceConfig notAsked = FieldInstanceConfig.of("photo", "node", "page", "Photo").withSettings(Map.of(
                FileItem.ALT_FIELD, false, FileItem.ALT_FIELD_REQUIRED, true));

        assertThat(IMAGE.defaultConstraints(storage, notAsked).getFirst().options())
                .containsEntry(FileItem.ALT_FIELD_REQUIRED, false)
                .containsEntry(FileItem.IMAGE, true)
                .containsEntry(FileItem.MIN_RESOLUTION, "");
    }

    @Test
    void theFieldUiShowsAFileFieldsCurrentLimits() {
        List<FormElement> form = FILE.instanceSettingsForm(Map.of(
                FileItem.FILE_EXTENSIONS, "pdf, .TXT", FileItem.MAX_FILESIZE, 2048L, FileItem.DESCRIPTION_FIELD, true));

        assertThat(form).extracting(FormElement::name).containsExactly(
                name(FileItem.FILE_EXTENSIONS), name(FileItem.MAX_FILESIZE), name(FileItem.DESCRIPTION_FIELD));
        assertThat(form).extracting(FormElement::value).containsExactly("pdf txt", 2048L, true);
    }

    @Test
    void aFileFieldSubmissionKeepsValidExtensionsAndAWholeSize() {
        Map<String, Object> values = FILE.instanceSettingsValues(Map.of(
                name(FileItem.FILE_EXTENSIONS), "PDF doc$ txt",
                name(FileItem.MAX_FILESIZE), "4096",
                name(FileItem.DESCRIPTION_FIELD), FormRenderer.CHECKED_VALUE));

        assertThat(values).containsEntry(FileItem.FILE_EXTENSIONS, "pdf txt")
                .containsEntry(FileItem.MAX_FILESIZE, 4096L)
                .containsEntry(FileItem.DESCRIPTION_FIELD, true);
    }

    @Test
    void aFileFieldSubmissionWithNothingValidFallsBackToItsDefaults() {
        Map<String, Object> values = FILE.instanceSettingsValues(Map.of(
                name(FileItem.FILE_EXTENSIONS), "$$", name(FileItem.MAX_FILESIZE), "lots"));

        assertThat(values).containsEntry(FileItem.FILE_EXTENSIONS, "txt")
                .containsEntry(FileItem.MAX_FILESIZE, 0L)
                .containsEntry(FileItem.DESCRIPTION_FIELD, false);
    }

    @Test
    void theFieldUiShowsAnImageFieldsPixelBoundsAndAlternativeTextChoices() {
        List<FormElement> form = IMAGE.instanceSettingsForm(IMAGE.defaultInstanceSettings());

        assertThat(form).extracting(FormElement::name).containsExactly(
                name(FileItem.FILE_EXTENSIONS), name(FileItem.MAX_FILESIZE), name(FileItem.MIN_RESOLUTION),
                name(FileItem.MAX_RESOLUTION), name(FileItem.ALT_FIELD), name(FileItem.ALT_FIELD_REQUIRED),
                name(FileItem.TITLE_FIELD));
        assertThat(IMAGE.instanceSettingsForm(Map.of()).get(2).value()).isEqualTo("");
    }

    @Test
    void anImageFieldSubmissionDropsPixelBoundsThatAreNotWidthByHeight() {
        Map<String, Object> values = IMAGE.instanceSettingsValues(Map.of(
                name(FileItem.MIN_RESOLUTION), "100x50",
                name(FileItem.MAX_RESOLUTION), "huge",
                name(FileItem.ALT_FIELD), FormRenderer.CHECKED_VALUE));

        assertThat(values).containsEntry(FileItem.FILE_EXTENSIONS, ImageFieldType.DEFAULT_EXTENSIONS)
                .containsEntry(FileItem.MIN_RESOLUTION, "100x50")
                .containsEntry(FileItem.MAX_RESOLUTION, "")
                .containsEntry(FileItem.ALT_FIELD, true)
                .containsEntry(FileItem.ALT_FIELD_REQUIRED, false);
    }

    @Test
    void aValueNamesItsFileByAWholeNumber() {
        assertThat(FileItem.fileId(Map.of(FileItem.TARGET_ID, 12))).contains(12L);
        assertThat(FileItem.fileId(Map.of(FileItem.TARGET_ID, "twelve"))).isEmpty();
        assertThat(FileItem.fileId(Map.of())).isEmpty();
        assertThat(FileItem.fileId(null)).isEmpty();
        assertThat(FileItem.fileIds(List.of(Map.of(FileItem.TARGET_ID, 1), "stray", Map.of(FileItem.TARGET_ID, 2))))
                .containsExactly(1L, 2L);
        assertThat(FileItem.fileIds(Map.of(FileItem.TARGET_ID, 3))).containsExactly(3L);
    }

    @Test
    void settingsReadBackAsTheirTypedValues() {
        assertThat(FileItem.extensions(null)).isEmpty();
        assertThat(FileItem.maxFilesize(null)).isZero();
        assertThat(FileItem.resolution(null)).isEmpty();
        assertThat(FileItem.resolution("tall")).isEmpty();
        assertThat(FileItem.resolution("640x480")).hasValueSatisfying(sides -> assertThat(sides).containsExactly(640, 480));
        assertThat(FileItem.part("plain", FileItem.ALT)).isEmpty();
        assertThat(UploadLimits.extension("README")).isEmpty();
        assertThat(UploadLimits.extension("Plan.PDF")).isEqualTo("pdf");
    }

    @Test
    void aSizeReadsInBytesKilobytesOrMegabytes() {
        assertThat(FileFormatter.readableSize(512)).isEqualTo("512 bytes");
        assertThat(FileFormatter.readableSize(2048)).isEqualTo("2 KB");
        assertThat(FileFormatter.readableSize(1536)).isEqualTo("1.5 KB");
        assertThat(FileFormatter.readableSize(3 * 1024 * 1024)).isEqualTo("3 MB");
    }
}
