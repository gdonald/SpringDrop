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
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.TextLongFieldType;
import dev.springdrop.kernel.field.types.TextWithSummaryFieldType;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.FieldWidgetPaths;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.FileUsageTracker;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EditorIntegrationTest.NoticeTypes.class)
class EditorIntegrationTest extends AbstractIntegrationTest {

    record Notice(long id, String label) {
    }

    static final EntityType NOTICE = EntityType.content("editor_notice", Notice.class)
            .withBundles("type", "editor_notice_type")
            .withRevisions();

    static final EntityType NOTICE_TYPE = EntityType.config("editor_notice_type", Notice.class);

    private static final String BUNDLE = "announcement";

    private static final long WRITER = 71L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TextFormatManager formats;

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FormRenderer renderer;

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
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @BeforeEach
    void anAnnouncementWithABodyAndAnIntro() {
        entityTypeManager.installStorage(NOTICE.id());
        bundles.save(NOTICE.id(), new BundleDefinition(BUNDLE, "Announcement"));
        fields.createStorage(new FieldStorageConfig("body", NOTICE.id(), TextLongFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("body", NOTICE.id(), BUNDLE, "Body"));
        fields.createStorage(new FieldStorageConfig("intro", NOTICE.id(), TextWithSummaryFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("intro", NOTICE.id(), BUNDLE, "Intro"));
    }

    @AfterEach
    void removeEverything() {
        queries.query(NOTICE.id()).ids().forEach(id -> entities.delete(NOTICE.id(), id));
        fields.fieldNames(NOTICE.id(), BUNDLE).forEach(field -> fields.deleteStorage(NOTICE.id(), field));
        bundles.delete(NOTICE.id(), BUNDLE);
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

    private static RequestPostProcessor writer(String... formatIds) {
        return user(new AccountPrincipal(WRITER, "edith", "", true,
                Arrays.stream(formatIds).map(TextFormatManager::permission).toList()));
    }

    private MockHttpServletResponse upload(String format, String name, byte[] content, RequestPostProcessor who)
            throws Exception {
        return mockMvc.perform(multipart(FieldWidgetPaths.EDITOR_UPLOAD)
                        .file(new MockMultipartFile(EditorUploadController.FILE, name, "image/png", content))
                        .param(EditorUploadController.FORMAT, format)
                        .with(csrf())
                        .with(who))
                .andReturn().getResponse();
    }

    private Element textInput(String field) {
        WidgetContext context = widgets.context(NOTICE.id(), BUNDLE, field);
        return Jsoup.parseBodyFragment(renderer.render(widgets.build(context, List.of(), 0)))
                .selectFirst("[data-editor-target]");
    }

    private static Map<String, Object> text(String html, String format) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(FormattedText.VALUE, html);
        value.put(FormattedText.FORMAT, format);
        return value;
    }

    private EntityData notice(Map<String, Object> values) {
        return new EntityData(NOTICE.id(), null, null, BUNDLE, "Opening hours", EntityData.DEFAULT_LANGCODE, null,
                values);
    }

    private ManagedFile stored(String scheme) throws IOException {
        byte[] content = png(40, 30);
        return files.store(new ByteArrayInputStream(content), "hall.png", content.length, scheme, WRITER);
    }

    @Test
    void theTextInputTellsTheEditorWhereImagesGoAndWhatTheyAreHeldTo() {
        Element input = textInput("body");

        assertThat(input.attr("data-editor-upload")).isEqualTo(FieldWidgetPaths.EDITOR_UPLOAD);
        assertThat(input.attr("data-editor-image-extensions")).isEqualTo("png gif jpg jpeg");
        assertThat(input.attr("data-editor-image-max-filesize")).isEqualTo(String.valueOf(32L * 1024 * 1024));
        assertThat(input.attr("data-editor-image-message-extension"))
                .isEqualTo("Only files with these extensions are allowed: png gif jpg jpeg.");
        assertThat(input.attr("data-editor-image-message-size")).isEqualTo("The file is larger than 33554432 bytes.");
    }

    @Test
    void aFormatThatShowsMarkupAsTextOffersTheEditorNoTags() {
        assertThat(formats.editorTags(formats.find(TextFormatManager.PLAIN_TEXT).orElseThrow())).isEmpty();
        assertThat(formats.editorTags(formats.find(TextFormatManager.BASIC_HTML).orElseThrow())).contains("img");
        assertThat(formats.editorTags(formats.find(TextFormatManager.RESTRICTED_HTML).orElseThrow()))
                .doesNotContain("img");
    }

    @Test
    void anImageUploadedThroughTheEditorIsStoredAndAnsweredWithItsAddressAndSize() throws Exception {
        MockHttpServletResponse response = upload(TextFormatManager.BASIC_HTML, "hall.png", png(40, 30),
                writer(TextFormatManager.BASIC_HTML));

        JsonNode answer = objectMapper.readTree(response.getContentAsString());
        ManagedFile stored = files.find(answer.get("id").asLong()).orElseThrow();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(answer.get("url").asString()).isEqualTo(files.url(stored));
        assertThat(List.of(answer.get("width").asInt(), answer.get("height").asInt())).containsExactly(40, 30);
        assertThat(stored.scheme()).isEqualTo(FileSchemes.PUBLIC);
        assertThat(stored.owner()).isEqualTo(WRITER);
        assertThat(stored.permanent()).isFalse();
    }

    @Test
    void anImageIsRefusedForAFormatThatDoesNotKeepImagesOrThatThePersonMayNotUse() throws Exception {
        assertThat(upload(TextFormatManager.RESTRICTED_HTML, "hall.png", png(4, 4),
                writer(TextFormatManager.RESTRICTED_HTML)).getStatus()).isEqualTo(403);
        assertThat(upload(TextFormatManager.BASIC_HTML, "hall.png", png(4, 4), writer()).getStatus())
                .isEqualTo(403);
        assertThat(upload("retired_format", "hall.png", png(4, 4), writer()).getContentAsString())
                .isEqualTo("This text format does not take images.");
        assertThat(upload(TextFormatManager.BASIC_HTML, "hall.png", png(4, 4), user("visitor"))
                .getContentAsString()).isEqualTo("Sign in to upload images.");
        assertThat(upload(TextFormatManager.BASIC_HTML, "hall.png", png(4, 4), request -> request).getStatus())
                .isEqualTo(403);
        assertThat(queries.query(FileEntityType.ID).count()).isZero();
    }

    @Test
    void anImageOfAnotherKindOrNotAnImageIsRefusedWithTheMessageTheEditorShows() throws Exception {
        MockHttpServletResponse wrongKind = upload(TextFormatManager.BASIC_HTML, "hall.svg", png(4, 4),
                writer(TextFormatManager.BASIC_HTML));
        MockHttpServletResponse notAnImage = upload(TextFormatManager.BASIC_HTML, "hall.png",
                "not a picture".getBytes(StandardCharsets.UTF_8), writer(TextFormatManager.BASIC_HTML));

        assertThat(wrongKind.getStatus()).isEqualTo(422);
        assertThat(wrongKind.getContentAsString()).isEqualTo(textInput("body").attr("data-editor-image-message-extension"));
        assertThat(notAnImage.getContentAsString()).isEqualTo("The file is not an image.");
        assertThat(queries.query(FileEntityType.ID).count()).isZero();
    }

    @Test
    void anImageInsertedIntoSavedTextIsKeptAndReleasedOnceTheTextNoLongerHasIt() throws IOException {
        ManagedFile inBody = stored(FileSchemes.PUBLIC);
        ManagedFile inSummary = stored(FileSchemes.PUBLIC);
        Map<String, Object> intro = text("<p>Welcome</p>", TextFormatManager.BASIC_HTML);
        intro.put(FormattedText.SUMMARY, "<img src='" + files.url(inSummary) + "' data-file-id='" + inSummary.id()
                + "'>");

        EntityData saved = entities.save(notice(Map.of(
                "body", text("<p><img src=\"" + files.url(inBody) + "\" data-file-id=\"" + inBody.id()
                        + "\" alt=\"Hall\"></p>", TextFormatManager.BASIC_HTML),
                "intro", intro)));

        assertThat(usage.filesUsedBy(FileUsageTracker.EDITOR_MODULE, NOTICE.id(), saved.id()))
                .containsExactlyInAnyOrder(inBody.id(), inSummary.id());
        assertThat(files.find(inBody.id())).map(ManagedFile::permanent).contains(true);

        entities.save(saved.withFields(Map.of("body", text("<p>No picture</p>", TextFormatManager.BASIC_HTML),
                "intro", text("<p>Welcome</p>", TextFormatManager.BASIC_HTML))), false);

        assertThat(usage.total(inBody.id())).isZero();
        assertThat(usage.total(inSummary.id())).isZero();
    }

    @Test
    void textNamingAPrivateFileDoesNotUseIt() throws IOException {
        ManagedFile secret = stored(FileSchemes.PRIVATE);

        entities.save(notice(Map.of("body", text("<img data-file-id=\"" + secret.id() + "\"><img data-file-id=999999>",
                TextFormatManager.BASIC_HTML))));

        assertThat(usage.total(secret.id())).isZero();
    }

    @Test
    void anImageInsertedThroughTheEditorRendersAtItsAddress() throws IOException {
        ManagedFile image = stored(FileSchemes.PUBLIC);

        String rendered = formats.process("<p><img src=\"" + files.url(image) + "\" alt=\"Hall\" width=\"40\" "
                + "data-file-id=\"" + image.id() + "\"></p>", TextFormatManager.BASIC_HTML);

        Element img = Jsoup.parseBodyFragment(rendered).selectFirst("img");
        assertThat(img.attr("src")).isEqualTo(files.url(image));
        assertThat(img.hasAttr("data-file-id")).isFalse();
    }

    @Test
    void theTextareaSubmittedWithoutTheEditorSavesTheText() {
        WidgetContext context = widgets.context(NOTICE.id(), BUNDLE, "body");
        Map<String, Object> submitted = Map.of("body:value", "<p>Closed <em>Mondays</em></p>",
                "body:format", TextFormatManager.BASIC_HTML);

        EntityData saved = entities.save(notice(Map.of("body", widgets.extract(context, submitted))));

        assertThat(entities.load(NOTICE.id(), saved.id()).orElseThrow().fields().get("body"))
                .isEqualTo(Map.of(FormattedText.VALUE, "<p>Closed <em>Mondays</em></p>",
                        FormattedText.FORMAT, TextFormatManager.BASIC_HTML));
    }

    @TestConfiguration
    static class NoticeTypes {

        @Bean
        EntityTypeProvider noticeTypes() {
            return () -> List.of(NOTICE, NOTICE_TYPE);
        }
    }
}
