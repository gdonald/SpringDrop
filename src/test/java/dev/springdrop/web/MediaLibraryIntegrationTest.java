package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.media.MediaEntityType;
import dev.springdrop.kernel.media.MediaLibraryWidget;
import dev.springdrop.kernel.media.MediaPermissions;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.media.MediaTestConfiguration;
import dev.springdrop.kernel.media.MediaType;
import dev.springdrop.kernel.media.MediaTypeManager;
import dev.springdrop.kernel.media.StubOEmbedClient;
import dev.springdrop.kernel.media.oembed.OEmbedResource;
import dev.springdrop.kernel.media.sources.DocumentSource;
import dev.springdrop.kernel.media.sources.ImageSource;
import dev.springdrop.kernel.media.sources.RemoteVideoSource;
import dev.springdrop.kernel.media.sources.VideoFileSource;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MediaTestConfiguration.class)
class MediaLibraryIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "library_page";

    private static final String FIELD = "field_media";

    private static final String VIDEO = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private static final long EDITH = 101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaService media;

    @Autowired
    private MediaTypeManager mediaTypes;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private StubOEmbedClient oembed;

    @Autowired
    private StateService state;

    @BeforeEach
    void aPageWithAMediaField() {
        media.createType("photo", "Photo", "", ImageSource.ID);
        media.createType("report", "Report", "", DocumentSource.ID);
        media.createType("talk", "Talk", "", RemoteVideoSource.ID);
        media.createType("clip", "Clip", "", VideoFileSource.ID);
        nodeTypes.save(NodeType.of(PAGE, "Library page"));
        fields.createStorage(new FieldStorageConfig(FIELD, NodeEntityType.ID, EntityReferenceFieldType.ID,
                FieldStorageConfig.UNLIMITED, Map.of(EntityReferenceFieldType.TARGET_TYPE, MediaEntityType.ID)));
        fields.createInstance(FieldInstanceConfig.of(FIELD, NodeEntityType.ID, PAGE, "Media").withSettings(Map.of(
                FieldWidgetManager.WIDGET_SETTING, MediaLibraryWidget.ID,
                EntityReferenceFieldType.TARGET_BUNDLES, List.of("photo", "report", "talk", "retired"))));
        oembed.reset();
    }

    @AfterEach
    void removeEverything() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        fields.deleteStorage(NodeEntityType.ID, FIELD);
        nodeTypes.delete(PAGE);
        queries.query(MediaEntityType.ID).ids().forEach(id -> media.delete(((Number) id).longValue()));
        mediaTypes.all().forEach(media::deleteType);
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
        state.remove("media.icons", "video");
        state.remove("media.icons", "document");
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private static RequestPostProcessor editor() {
        List<String> granted = new ArrayList<>(List.of(MediaPermissions.VIEW_MEDIA, MediaPermissions.create("photo"),
                MediaPermissions.create("talk"), MediaPermissions.create("report"), MediaPermissions.create("clip"),
                NodePermissions.create(PAGE), NodePermissions.ACCESS_CONTENT));
        return user(new AccountPrincipal(EDITH, "edith", "", true, granted));
    }

    private static RequestPostProcessor viewer() {
        return user(new AccountPrincipal(EDITH + 1, "viewer", "", true, List.of(MediaPermissions.VIEW_MEDIA)));
    }

    private EntityData photo(String name, boolean published) throws IOException {
        ManagedFile image = files.store(png(), name + ".png", FileSchemes.PUBLIC, EDITH);
        EntityData blank = media.create(mediaTypes.find("photo").orElseThrow(), EDITH);
        Map<String, Object> values = new LinkedHashMap<>(blank.fields());
        values.put(MediaType.sourceFieldFor("photo"), Map.of(FileItem.TARGET_ID, image.id(), FileItem.ALT, name));
        values.put(BaseFieldDefinition.STATUS, published);
        return media.save(new EntityData(MediaEntityType.ID, null, null, "photo", name, EntityData.DEFAULT_LANGCODE,
                null, values));
    }

    private static String libraryPath() {
        return MediaLibraryWidget.LIBRARY_PATH + "?entity_type=node&bundle=" + PAGE + "&field=" + FIELD;
    }

    private Document library(String query, RequestPostProcessor who) throws Exception {
        return Jsoup.parseBodyFragment(mockMvc.perform(get(libraryPath() + query).with(who)).andReturn()
                .getResponse().getContentAsString());
    }

    private MockHttpServletResponse add(String type, Map<String, String> params, MockMultipartFile file,
            RequestPostProcessor who) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(MediaLibraryController.ADD_PATH);
        if (file != null) {
            request.file(file);
        }
        request.param("entity_type", "node").param("bundle", PAGE).param("field", FIELD).param("type", type);
        params.forEach(request::param);
        return mockMvc.perform(request.with(csrf()).with(who)).andReturn().getResponse();
    }

    private MockHttpServletResponse createPage(String title, List<Object> mediaIds) throws Exception {
        MockHttpServletRequestBuilder request = post(NodeController.ADD_PATH + "/" + PAGE).with(csrf())
                .param("title", title);
        for (int delta = 0; delta < mediaIds.size(); delta++) {
            request.param(FIELD + "[" + delta + "]:target_id", String.valueOf(mediaIds.get(delta)));
        }
        return mockMvc.perform(request.with(editor())).andReturn().getResponse();
    }

    @Test
    void aMediaItemCreatedOnceIsReusedOnSeveralPagesThroughTheLibrary() throws Exception {
        MockHttpServletResponse uploaded = add("photo", Map.of("name", "Main hall"),
                new MockMultipartFile("file", "hall.png", "image/png", png()), editor());
        Document item = Jsoup.parseBodyFragment(uploaded.getContentAsString());
        String mediaId = item.selectFirst("[data-media-library-choice]").val();

        Document listed = library("&type=photo", editor());
        String inputs = listed.selectFirst("[data-media-library-choice][value=" + mediaId + "]")
                .closest("[data-media-library-item]").selectFirst("template").html();
        createPage("Opening hours", List.of(Long.parseLong(mediaId)));
        createPage("Directions", List.of(Long.parseLong(mediaId)));

        assertThat(item.selectFirst("[data-media-library-choice]").hasAttr("checked")).isTrue();
        assertThat(inputs).contains("name=\"field_media[__delta__]:target_id\"").contains("value=\"" + mediaId + "\"");
        List<Object> referenced = queries.query(NodeEntityType.ID).ids().stream()
                .map(id -> nodes.find(((Number) id).longValue()).orElseThrow().fields().get(FIELD))
                .toList();
        assertThat(referenced).hasSize(2).allSatisfy(value -> assertThat(String.valueOf(value)).isEqualTo(mediaId));
        assertThat(queries.query(MediaEntityType.ID).count()).isOne();
    }

    @Test
    void theLibraryHasATabPerTypeTheFieldTakesAndListsWhatThePersonMayView() throws Exception {
        EntityData shown = photo("Garden", true);
        EntityData hidden = photo("Draft", false);

        Document listed = library("", viewer());

        assertThat(listed.select("[data-media-library-tab]")).extracting(tab -> tab.text())
                .containsExactly("Photo", "Report", "Talk");
        assertThat(listed.selectFirst("[data-media-library-tab].active").text()).isEqualTo("Photo");
        assertThat(listed.select("[data-media-library-choice]")).extracting(choice -> choice.val())
                .containsExactly(String.valueOf(shown.id()));
        assertThat(listed.select("[data-media-library-upload]")).isEmpty();
        assertThat(String.valueOf(hidden.id())).isNotEqualTo(String.valueOf(shown.id()));
    }

    @Test
    void theLibraryIsNarrowedByName() throws Exception {
        photo("Garden", true);
        EntityData hall = photo("Main hall", true);

        Document listed = Jsoup.parseBodyFragment(mockMvc.perform(get(MediaLibraryWidget.LIBRARY_PATH)
                .param("entity_type", "node").param("bundle", PAGE).param("field", FIELD).param("type", "photo")
                .param("q", " hall ").with(editor())).andReturn().getResponse().getContentAsString());

        assertThat(listed.select("[data-media-library-choice]")).extracting(choice -> choice.val())
                .containsExactly(String.valueOf(hall.id()));
        assertThat(listed.selectFirst("input[name=q]").val()).isEqualTo("hall");
    }

    @Test
    void theRemoteVideoTabAsksForAnAddressAndAnEmptyTabSaysSo() throws Exception {
        Document listed = library("&type=talk", editor());

        assertThat(listed.select("input[name=address]")).hasSize(1);
        assertThat(listed.select("input[name=file]")).isEmpty();
        assertThat(listed.select("[data-media-library-empty]")).hasSize(1);
        assertThat(library("&type=photo", editor()).selectFirst("input[name=file]").attr("accept"))
                .isEqualTo(".png,.gif,.jpg,.jpeg");
    }

    @Test
    void aRemoteVideoIsAddedFromItsAddress() throws Exception {
        oembed.describe(VIDEO, new OEmbedResource("video", "Opening night", "YouTube", "", "", 0, 0));

        MockHttpServletResponse added = add("talk", Map.of("address", " " + VIDEO + " "), null, editor());

        assertThat(Jsoup.parseBodyFragment(added.getContentAsString()).text()).contains("Opening night");
    }

    @Test
    void aDocumentIsAddedFromItsFileNamedAsGiven() throws Exception {
        MockHttpServletResponse added = add("report", Map.of("name", "Annual report"),
                new MockMultipartFile("file", "annual.pdf", "application/pdf", new byte[12]), editor());

        assertThat(Jsoup.parseBodyFragment(added.getContentAsString()).text()).contains("Annual report");
        assertThat(media.find(Long.parseLong(Jsoup.parseBodyFragment(added.getContentAsString())
                .selectFirst("[data-media-library-choice]").val())).orElseThrow()
                .fields().get(MediaType.sourceFieldFor("report"))).isInstanceOf(Map.class);
    }

    @Test
    void aFieldHoldingOneItemTellsTheBrowserSoAndThumbnailsFallBackToTheOriginal() throws Exception {
        EntityData garden = photo("Garden", true);
        WidgetContext unlimited = widgets.context(NodeEntityType.ID, PAGE, FIELD);
        WidgetContext single = new WidgetContext(new FieldStorageConfig(FIELD, NodeEntityType.ID,
                EntityReferenceFieldType.ID, 1, unlimited.storage().settings()), unlimited.instance());
        MediaLibraryWidget widget = (MediaLibraryWidget) widgets.widget(unlimited);

        assertThat(Jsoup.parseBodyFragment(renderer.render(widget.elementForAll(single, List.of())))
                .selectFirst("[data-media-field]").attr("data-cardinality")).isEqualTo("1");
        imageStyles.delete("thumbnail");
        try {
            assertThat(widget.thumbnail(garden)).hasValueSatisfying(address ->
                    assertThat(address).doesNotContain("/styles/"));
        } finally {
            imageStyles.installDefaults();
        }
    }

    @Autowired
    private ImageStyleManager imageStyles;

    @Test
    void anAdditionTheSourceRefusesIsAnsweredWithTheReasonAndKeepsNoFile() throws Exception {
        MockHttpServletResponse badAddress = add("talk", Map.of("address", "https://example.com/v"), null, editor());
        MockHttpServletResponse badImage = add("photo", Map.of(),
                new MockMultipartFile("file", "hall.png", "image/png", "not a picture".getBytes()), editor());

        assertThat(badAddress.getStatus()).isEqualTo(422);
        assertThat(badAddress.getContentAsString())
                .isEqualTo("The address is not from a site the media library embeds from.");
        assertThat(badImage.getContentAsString()).isEqualTo("The file is not an image.");
        assertThat(queries.query(FileEntityType.ID).count()).isZero();
    }

    @Test
    void anUploadBreakingTheTypesLimitsOrMissingIsRefused() throws Exception {
        MockHttpServletResponse wrongKind = add("photo", Map.of(),
                new MockMultipartFile("file", "hall.svg", "image/svg+xml", png()), editor());
        MockHttpServletResponse missing = add("photo", Map.of(), null, editor());
        MockHttpServletResponse empty = add("photo", Map.of(),
                new MockMultipartFile("file", "hall.png", "image/png", new byte[0]), editor());
        MockHttpServletResponse longName = add("photo", Map.of("name", "n".repeat(256)),
                new MockMultipartFile("file", "hall.png", "image/png", png()), editor());

        assertThat(wrongKind.getContentAsString()).isEqualTo("Only files with these extensions are allowed: "
                + "png gif jpg jpeg.");
        assertThat(missing.getContentAsString()).isEqualTo("Choose a file to upload.");
        assertThat(empty.getContentAsString()).isEqualTo("Choose a file to upload.");
        assertThat(longName.getContentAsString()).isEqualTo("The name is longer than 255 characters.");
    }

    @Test
    void addingIsRefusedOutsideTheFieldsTypesOrWithoutCreateAccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "hall.png", "image/png", png());

        assertThat(add("clip", Map.of(), file, editor()).getStatus()).isEqualTo(403);
        assertThat(add("photo", Map.of(), file, viewer()).getStatus()).isEqualTo(403);
        assertThat(add("photo", Map.of(), file, user("visitor")).getContentAsString())
                .isEqualTo("You may not add media of this type here.");
        assertThat(add("photo", Map.of(), file, request -> request).getStatus()).isEqualTo(403);
    }

    @Test
    void theLibraryOfAFieldThatTakesNoMediaIsNotFound() throws Exception {
        fields.createStorage(new FieldStorageConfig("field_link", NodeEntityType.ID, EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, NodeEntityType.ID)));
        fields.createInstance(FieldInstanceConfig.of("field_link", NodeEntityType.ID, PAGE, "Link"));
        fields.createStorage(new FieldStorageConfig("field_note", NodeEntityType.ID, "string", 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("field_note", NodeEntityType.ID, PAGE, "Note"));
        try {
            for (String field : List.of("field_link", "field_note", "field_missing")) {
                assertThat(mockMvc.perform(get(MediaLibraryWidget.LIBRARY_PATH).param("entity_type", "node")
                        .param("bundle", PAGE).param("field", field).with(editor())).andReturn().getResponse()
                        .getStatus()).isEqualTo(404);
            }
            mediaTypes.all().forEach(media::deleteType);
            assertThat(mockMvc.perform(get(libraryPath()).with(editor())).andReturn().getResponse().getStatus())
                    .isEqualTo(404);
        } finally {
            fields.deleteStorage(NodeEntityType.ID, "field_link");
            fields.deleteStorage(NodeEntityType.ID, "field_note");
        }
    }

    @Test
    void aFieldWithoutTargetBundlesTakesEveryType() {
        fields.createInstance(fields.findInstance(NodeEntityType.ID, PAGE, FIELD).orElseThrow()
                .withSettings(Map.of(EntityReferenceFieldType.TARGET_BUNDLES, "photo")));
        WidgetContext context = widgets.context(NodeEntityType.ID, PAGE, FIELD);

        assertThat(MediaLibraryWidget.allowedTypes(context, List.of("photo", "report"))).containsExactly("photo",
                "report");
    }

    @Test
    void theWidgetListsTheChosenMediaThePersonMayViewAndReadsThemBackInOrder() throws Exception {
        EntityData garden = photo("Garden", true);
        EntityData hall = photo("Main hall", true);
        EntityData draft = photo("Draft", false);
        WidgetContext context = widgets.context(NodeEntityType.ID, PAGE, FIELD);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("viewer", null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                MediaPermissions.VIEW_MEDIA))));
        try {
            Document form = Jsoup.parseBodyFragment(renderer.render(widgets.build(context,
                    List.of(garden.id(), draft.id(), "gone", 999_999L), 0)));
            Map<String, Object> submitted = new LinkedHashMap<>();
            submitted.put(FIELD + "[0]:target_id", String.valueOf(garden.id()));
            submitted.put(FIELD + "[0]:weight", "3");
            submitted.put(FIELD + "[1]:target_id", String.valueOf(hall.id()));
            submitted.put(FIELD + "[1]:weight", "first");
            submitted.put(FIELD + "[2]:target_id", String.valueOf(draft.id()));
            submitted.put(FIELD + "[3]:target_id", String.valueOf(hall.id()));
            submitted.put(FIELD + "[4]:target_id", String.valueOf(garden.id()));
            submitted.put(FIELD + "[4]:remove", FormRenderer.CHECKED_VALUE);
            submitted.put("title", "Opening hours");

            assertThat(form.select("[data-media-item]")).extracting(item -> item.attr("data-media-item"))
                    .containsExactly(String.valueOf(garden.id()));
            assertThat(form.selectFirst("[data-media-library-open]").attr("href")).isEqualTo(libraryPath());
            assertThat(form.selectFirst("[data-media-field]").attr("data-cardinality")).isEqualTo("-1");
            assertThat(widgets.extract(context, submitted)).containsExactly(hall.id(), garden.id());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void anItemWithoutAThumbnailOrWithoutTheThumbnailStyleShowsWhatItHas() throws Exception {
        EntityData garden = photo("Garden", true);
        MediaLibraryWidget widget = (MediaLibraryWidget) widgets.widget(widgets.context(NodeEntityType.ID, PAGE,
                FIELD));
        Map<String, Object> values = new LinkedHashMap<>(garden.fields());
        values.remove(MediaEntityType.THUMBNAIL);

        assertThat(widget.thumbnail(garden.withFields(values))).isEmpty();
        assertThat(widget.thumbnail(garden)).hasValueSatisfying(address -> assertThat(address).contains("/styles/"));
        assertThat(widget.id()).isEqualTo(MediaLibraryWidget.ID);
    }
}
