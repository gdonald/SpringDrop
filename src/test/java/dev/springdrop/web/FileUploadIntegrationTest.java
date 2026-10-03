package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileFieldType;
import dev.springdrop.kernel.file.FileGenericWidget;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.FileWidget;
import dev.springdrop.kernel.file.ImageFieldType;
import dev.springdrop.kernel.file.ImageWidget;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.file.UploadLimits;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FileUploadIntegrationTest.GalleryTypes.class)
class FileUploadIntegrationTest extends AbstractIntegrationTest {

    record Gallery(long id, String label) {
    }

    static final EntityType GALLERY = EntityType.content("gallery", Gallery.class)
            .withBundles("type", "gallery_type");

    static final EntityType GALLERY_TYPE = EntityType.config("gallery_type", Gallery.class);

    private static final String BUNDLE = "exhibition";

    private static final long EDITH = 41L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private BundleManager bundles;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @BeforeEach
    void anExhibitionWithPhotosAndACatalog() {
        entityTypeManager.installStorage(GALLERY.id());
        bundles.save(GALLERY.id(), new BundleDefinition(BUNDLE, "Exhibition"));
        fields.createStorage(new FieldStorageConfig("photos", GALLERY.id(), ImageFieldType.ID,
                FieldStorageConfig.UNLIMITED, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("photos", GALLERY.id(), BUNDLE, "Photos").withSettings(Map.of(
                FileItem.FILE_EXTENSIONS, "png", FileItem.MAX_FILESIZE, 4096L,
                FileItem.ALT_FIELD, true, FileItem.ALT_FIELD_REQUIRED, true, FileItem.TITLE_FIELD, true,
                FileItem.MIN_RESOLUTION, "10x10", FileItem.MAX_RESOLUTION, "300x300")));
        fields.createStorage(new FieldStorageConfig("catalog", GALLERY.id(), FileFieldType.ID, 1,
                Map.of(FileItem.URI_SCHEME, FileSchemes.PRIVATE)));
        fields.createInstance(FieldInstanceConfig.of("catalog", GALLERY.id(), BUNDLE, "Catalog").withSettings(Map.of(
                FileItem.FILE_EXTENSIONS, "", FileItem.MAX_FILESIZE, 0L, FileItem.DESCRIPTION_FIELD, true)));
        fields.createStorage(new FieldStorageConfig("caption", GALLERY.id(), "string", 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("caption", GALLERY.id(), BUNDLE, "Caption"));
    }

    @AfterEach
    void removeEverything() {
        queries.query(GALLERY.id()).ids().forEach(id -> entities.delete(GALLERY.id(), id));
        fields.fieldNames(GALLERY.id(), BUNDLE).forEach(field -> fields.deleteStorage(GALLERY.id(), field));
        bundles.delete(GALLERY.id(), BUNDLE);
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private static RequestPostProcessor edith() {
        return user(new AccountPrincipal(EDITH, "edith", "", true, List.of()));
    }

    private MockHttpServletResponse upload(String field, String name, byte[] content, String delta,
            RequestPostProcessor who) throws Exception {
        return mockMvc.perform(multipart(FileWidget.UPLOAD_PATH)
                        .file(new MockMultipartFile(FileUploadController.FILE, name, "application/octet-stream", content))
                        .param(FieldWidgetController.ENTITY_TYPE, GALLERY.id())
                        .param(FieldWidgetController.BUNDLE, BUNDLE)
                        .param(FieldWidgetController.FIELD, field)
                        .param(FileUploadController.DELTA, delta)
                        .with(csrf())
                        .with(who))
                .andReturn().getResponse();
    }

    private WidgetContext context(String field) {
        return widgets.context(GALLERY.id(), BUNDLE, field);
    }

    private Element uploadInput(String field) {
        return Jsoup.parseBodyFragment(renderer.render(widgets.build(context(field), List.of(), 0)))
                .selectFirst("input[type=file]");
    }

    private long fileCount() {
        return queries.query(FileEntityType.ID).count();
    }

    @Test
    void aValidImageIsStoredAsATemporaryFileOfTheUploaderAndAnsweredWithItsInputs() throws Exception {
        MockHttpServletResponse response = upload("photos", "Opening night.png", png(40, 30), "3", edith());

        Document item = Jsoup.parseBodyFragment(response.getContentAsString());
        long fileId = Long.parseLong(item.selectFirst("input[name=photos[3]:target_id]").val());
        ManagedFile stored = files.find(fileId).orElseThrow();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(stored.owner()).isEqualTo(EDITH);
        assertThat(stored.permanent()).isFalse();
        assertThat(item.selectFirst("[data-file-preview]").attr("data-file-preview")).isEqualTo(files.url(stored));
        assertThat(item.select("input[name=photos[3]:alt]")).hasSize(1);
    }

    @Test
    void anUploadedImageSavedWithTheFormPersistsWithItsSize() throws Exception {
        MockHttpServletResponse response = upload("photos", "hall.png", png(40, 30), "0", edith());
        String fileId = Jsoup.parseBodyFragment(response.getContentAsString())
                .selectFirst("input[name=photos[0]:target_id]").val();

        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("photos[0]:target_id", fileId);
        submitted.put("photos[0]:alt", "The main hall");
        submitted.put("photos[0]:title", "Opening night");
        submitted.put("photos[0]:weight", "0");
        EntityData saved = entities.save(new EntityData(GALLERY.id(), null, null, BUNDLE, "Autumn show",
                EntityData.DEFAULT_LANGCODE, null, Map.of("photos", widgets.extract(context("photos"), submitted))));

        Object photo = entities.load(GALLERY.id(), saved.id()).orElseThrow().fields().get("photos");
        assertThat(List.of(FileItem.part(photo, FileItem.ALT), FileItem.part(photo, FileItem.TITLE),
                FileItem.part(photo, FileItem.WIDTH), FileItem.part(photo, FileItem.HEIGHT)))
                .containsExactly("The main hall", "Opening night", "40", "30");
        assertThat(files.find(Long.parseLong(fileId))).map(ManagedFile::permanent).contains(true);
    }

    @Test
    void aFileWithAWrongExtensionIsRefusedWithTheMessageTheBrowserShows() throws Exception {
        MockHttpServletResponse response = upload("photos", "hall.gif", png(40, 30), "0", edith());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).isEqualTo(uploadInput("photos").attr("data-message-extension"))
                .isEqualTo("Only files with these extensions are allowed: png.");
        assertThat(fileCount()).isZero();
    }

    @Test
    void aFileOverTheSizeLimitIsRefusedWithTheMessageTheBrowserShows() throws Exception {
        MockHttpServletResponse response = upload("photos", "hall.png", new byte[4097], "0", edith());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).isEqualTo(uploadInput("photos").attr("data-message-size"))
                .isEqualTo("The file is larger than 4096 bytes.");
        assertThat(fileCount()).isZero();
    }

    @Test
    void aFileThatIsNotAnImageIsRefusedAndNotKept() throws Exception {
        MockHttpServletResponse response = upload("photos", "hall.png",
                "not a picture".getBytes(StandardCharsets.UTF_8), "0", edith());

        assertThat(response.getContentAsString()).isEqualTo(uploadInput("photos").attr("data-message-not-image"));
        assertThat(fileCount()).isZero();
    }

    @Test
    void anImageOutsideThePixelBoundsIsRefusedWithTheMessageTheBrowserShows() throws Exception {
        assertThat(upload("photos", "hall.png", png(5, 30), "0", edith()).getContentAsString())
                .isEqualTo(uploadInput("photos").attr("data-message-too-small"));
        assertThat(upload("photos", "hall.png", png(301, 30), "0", edith()).getContentAsString())
                .isEqualTo(uploadInput("photos").attr("data-message-too-large"));
    }

    @Test
    void aFileFieldStoresUploadsInTheSchemeItNames() throws Exception {
        MockHttpServletResponse response = upload("catalog", "catalog.pdf", new byte[12], "-2", edith());

        Document item = Jsoup.parseBodyFragment(response.getContentAsString());
        ManagedFile stored = files.find(Long.parseLong(item.selectFirst("input[name=catalog[0]:target_id]").val()))
                .orElseThrow();
        assertThat(stored.scheme()).isEqualTo(FileSchemes.PRIVATE);
        assertThat(item.select("[data-file-preview]")).isEmpty();
        assertThat(item.select("input[name=catalog[0]:description]")).hasSize(1);
    }

    @Test
    void someoneNotSignedInCannotUpload() throws Exception {
        MockHttpServletResponse response = upload("catalog", "catalog.pdf", new byte[12], "0",
                user("visitor"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEqualTo("Sign in to upload files.");
    }

    @Test
    void anAnonymousVisitorCannotUpload() throws Exception {
        MockHttpServletResponse response = upload("catalog", "catalog.pdf", new byte[12], "0", request -> request);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(fileCount()).isZero();
    }

    @Test
    void anUploadToAFieldThatIsMissingOrTakesNoFilesIsNotFound() throws Exception {
        assertThat(upload("program", "program.pdf", new byte[12], "0", edith()).getStatus()).isEqualTo(404);
        assertThat(upload("caption", "caption.txt", new byte[12], "0", edith()).getStatus()).isEqualTo(404);
    }

    @Test
    void theUploadInputCarriesTheFieldsLimitsForTheBrowser() {
        Element photos = uploadInput("photos");
        Element catalog = uploadInput("catalog");

        assertThat(widgets.widget(context("photos"))).isInstanceOf(ImageWidget.class);
        assertThat(widgets.widget(context("catalog"))).isInstanceOf(FileGenericWidget.class);
        assertThat(List.of(photos.attr("accept"), photos.attr("data-file-extensions"),
                photos.attr("data-max-filesize"), photos.attr("data-min-resolution"),
                photos.attr("data-max-resolution"), photos.attr("data-cardinality"), photos.attr("data-image")))
                .containsExactly(".png", "png", "4096", "10x10", "300x300", "-1", "true");
        assertThat(photos.hasAttr("multiple")).isTrue();
        assertThat(photos.attr("data-dangerous-extensions")).contains("php");
        assertThat(catalog.hasAttr("accept")).isFalse();
        assertThat(catalog.hasAttr("multiple")).isFalse();
        assertThat(catalog.hasAttr("data-image")).isFalse();
        assertThat(catalog.attr("data-max-filesize")).isEqualTo(String.valueOf(32L * 1024 * 1024));
        assertThat(catalog.attr("data-cardinality")).isEqualTo("1");
    }

    @Test
    void theUploadInputDescribesWhatItTakes() {
        Document photos = Jsoup.parseBodyFragment(renderer.render(widgets.build(context("photos"), List.of(), 0)));
        Document catalog = Jsoup.parseBodyFragment(renderer.render(widgets.build(context("catalog"), List.of(), 0)));

        assertThat(photos.text()).contains("Add an image").contains("Allowed: png. Up to 4 KB.");
        assertThat(catalog.text()).contains("Add a file").contains("Any kind of file. Up to 32 MB.");
    }

    @Test
    void attachedFilesAreListedWithTheirInputsInOrder() throws IOException {
        ManagedFile first = files.store(new ByteArrayInputStream(png(40, 30)), "first.png", 100,
                FileSchemes.PUBLIC, EDITH);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(FileItem.TARGET_ID, first.id());
        value.put(FileItem.ALT, "Front door");
        value.put(FileItem.TITLE, "Entrance");

        Document form = Jsoup.parseBodyFragment(renderer.render(widgets.build(context("photos"),
                List.of(value, Map.of(FileItem.TARGET_ID, 999_999L)), 0)));

        assertThat(form.select("[data-file-item]")).hasSize(2);
        assertThat(form.selectFirst("input[name=photos[0]:alt]").val()).isEqualTo("Front door");
        assertThat(form.selectFirst("input[name=photos[0]:title]").val()).isEqualTo("Entrance");
        assertThat(form.selectFirst("input[name=photos[0]:alt]").hasAttr("required")).isTrue();
        assertThat(form.selectFirst("input[name=photos[1]:weight]").val()).isEqualTo("1");
        assertThat(form.text()).contains("first.png").contains("This file is no longer stored.");
    }

    @Test
    void submittedFilesAreReadInTheirOrderLeavingOutRemovedAndMissingOnes() throws IOException {
        ManagedFile hall = files.store(new ByteArrayInputStream(png(40, 30)), "hall.png", 100,
                FileSchemes.PUBLIC, EDITH);
        ManagedFile stage = files.store(new ByteArrayInputStream(png(20, 20)), "stage.png", 100,
                FileSchemes.PUBLIC, EDITH);
        ManagedFile foyer = files.store(new ByteArrayInputStream(png(20, 20)), "foyer.png", 100,
                FileSchemes.PUBLIC, EDITH);
        Map<String, Object> submitted = new LinkedHashMap<>();
        submitted.put("photos[0]:target_id", hall.id());
        submitted.put("photos[0]:weight", "5");
        submitted.put("photos[1]:target_id", stage.id());
        submitted.put("photos[1]:weight", "first");
        submitted.put("photos[2]:target_id", foyer.id());
        submitted.put("photos[2]:remove", FormRenderer.CHECKED_VALUE);
        submitted.put("photos[3]:target_id", "999999");
        submitted.put("photos[4]:target_id", "");
        submitted.put("photos:upload", "");
        submitted.put("caption", "Autumn");

        List<Object> values = widgets.extract(context("photos"), submitted);

        assertThat(values).extracting(value -> FileItem.fileId(value).orElseThrow())
                .containsExactly(stage.id(), hall.id());
        assertThat(FileItem.part(values.get(1), FileItem.WIDTH)).isEqualTo("40");
    }

    @Test
    void aFileIsSavedListedWithItsDescriptionWhenTheFieldAsksForOne() throws IOException {
        ManagedFile catalog = files.store(new ByteArrayInputStream(new byte[12]), "catalog.pdf", 12,
                FileSchemes.PRIVATE, EDITH);

        List<Object> values = widgets.extract(context("catalog"), Map.of(
                "catalog[0]:target_id", String.valueOf(catalog.id()), "catalog[0]:description", " Price list "));

        assertThat(values).containsExactly(Map.of(FileItem.TARGET_ID, catalog.id(), FileItem.DISPLAY, true,
                FileItem.DESCRIPTION, "Price list"));
    }

    @Test
    void aFileFieldThatAsksForNoDescriptionSavesNone() throws IOException {
        fields.createInstance(fields.findInstance(GALLERY.id(), BUNDLE, "catalog").orElseThrow()
                .withSettings(Map.of(FileItem.DESCRIPTION_FIELD, false)));
        ManagedFile catalog = files.store(new ByteArrayInputStream(new byte[12]), "catalog.pdf", 12,
                FileSchemes.PRIVATE, EDITH);

        List<Object> values = widgets.extract(context("catalog"), Map.of(
                "catalog[0]:target_id", String.valueOf(catalog.id()), "catalog[0]:description", "Price list"));
        Document form = Jsoup.parseBodyFragment(renderer.render(widgets.build(context("catalog"), values, 0)));

        assertThat(values).containsExactly(Map.of(FileItem.TARGET_ID, catalog.id(), FileItem.DISPLAY, true));
        assertThat(form.select("input[name=catalog[0]:description]")).isEmpty();
    }

    @Test
    void anImageFieldThatAsksForNoAlternativeTextOrTitleShowsNeither() throws IOException {
        fields.createInstance(fields.findInstance(GALLERY.id(), BUNDLE, "photos").orElseThrow()
                .withSettings(Map.of(FileItem.ALT_FIELD, false, FileItem.TITLE_FIELD, false)));
        ManagedFile hall = files.store(new ByteArrayInputStream(png(40, 30)), "hall.png", 100,
                FileSchemes.PUBLIC, EDITH);

        Document form = Jsoup.parseBodyFragment(renderer.render(widgets.build(context("photos"),
                List.of(Map.of(FileItem.TARGET_ID, hall.id())), 0)));

        assertThat(form.select("input[name=photos[0]:alt]")).isEmpty();
        assertThat(form.select("input[name=photos[0]:title]")).isEmpty();
    }

    @Test
    void anOptionalAlternativeTextIsNotMarkedRequired() throws IOException {
        fields.createInstance(fields.findInstance(GALLERY.id(), BUNDLE, "photos").orElseThrow()
                .withSettings(Map.of(FileItem.ALT_FIELD, true, FileItem.ALT_FIELD_REQUIRED, false)));
        ManagedFile hall = files.store(new ByteArrayInputStream(png(40, 30)), "hall.png", 100,
                FileSchemes.PUBLIC, EDITH);

        Document form = Jsoup.parseBodyFragment(renderer.render(widgets.build(context("photos"),
                List.of(Map.of(FileItem.TARGET_ID, hall.id())), 0)));

        assertThat(form.selectFirst("input[name=photos[0]:alt]").hasAttr("required")).isFalse();
    }

    @Test
    void aFieldsLimitAboveTheSitesIsHeldToTheSites() {
        UploadLimits limits = new UploadLimits(List.of("pdf"), 10_000, false, "", "");

        assertThat(limits.cappedAt(500).maxFilesize()).isEqualTo(500);
        assertThat(limits.cappedAt(50_000).maxFilesize()).isEqualTo(10_000);
    }

    @TestConfiguration
    static class GalleryTypes {

        @Bean
        EntityTypeProvider galleryTypes() {
            return () -> List.of(GALLERY, GALLERY_TYPE);
        }
    }
}
