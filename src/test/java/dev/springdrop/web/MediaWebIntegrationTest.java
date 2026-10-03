package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.media.MediaEntityType;
import dev.springdrop.kernel.media.MediaIcons;
import dev.springdrop.kernel.media.MediaPermissions;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.media.MediaTestConfiguration;
import dev.springdrop.kernel.media.MediaType;
import dev.springdrop.kernel.media.MediaTypeManager;
import dev.springdrop.kernel.media.StubOEmbedClient;
import dev.springdrop.kernel.media.oembed.OEmbedEmbeds;
import dev.springdrop.kernel.media.oembed.OEmbedFormatter;
import dev.springdrop.kernel.media.oembed.OEmbedResource;
import dev.springdrop.kernel.media.sources.DocumentSource;
import dev.springdrop.kernel.media.sources.ImageSource;
import dev.springdrop.kernel.media.sources.RemoteVideoSource;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewOptions;
import dev.springdrop.kernel.views.ViewRenderer;
import dev.springdrop.kernel.views.handlers.NoneAccess;
import dev.springdrop.kernel.views.rows.EntityRow;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MediaTestConfiguration.class)
class MediaWebIntegrationTest extends AbstractIntegrationTest {

    private static final String VIDEO = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private static final long EDITH = 91L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaService media;

    @Autowired
    private MediaTypeManager types;

    @Autowired
    private StubOEmbedClient oembed;

    @Autowired
    private OEmbedEmbeds embeds;

    @Autowired
    private OEmbedFormatter oembedFormatter;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ImageStyleManager imageStyles;

    @Autowired
    private StateService state;

    @Autowired
    private ViewRenderer viewRenderer;

    @BeforeEach
    void photosAndReports() {
        media.createType("photo", "Photo", "Photographs.", ImageSource.ID);
        media.createType("report", "Report", "Annual reports.", DocumentSource.ID);
        media.createType("talk", "Talk", "", RemoteVideoSource.ID);
        oembed.reset();
    }

    @AfterEach
    void removeEverything() {
        queries.query(MediaEntityType.ID).ids().forEach(id -> media.delete(((Number) id).longValue()));
        types.all().forEach(media::deleteType);
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
        List.of(MediaIcons.DOCUMENT, MediaIcons.VIDEO).forEach(kind -> state.remove("media.icons", kind));
    }

    private static RequestPostProcessor as(long accountId, String... granted) {
        return user(new AccountPrincipal(accountId, "edith", "", true, List.of(granted)));
    }

    private static RequestPostProcessor typeAdministrator() {
        return as(1_000L, MediaPermissions.ADMINISTER_MEDIA_TYPES);
    }

    private static RequestPostProcessor writer() {
        List<String> granted = new ArrayList<>();
        for (String type : List.of("photo", "report", "talk")) {
            granted.addAll(List.of(MediaPermissions.create(type), MediaPermissions.editOwn(type),
                    MediaPermissions.deleteOwn(type)));
        }
        granted.add(MediaPermissions.VIEW_MEDIA);
        granted.add(MediaPermissions.ACCESS_MEDIA_OVERVIEW);
        return as(EDITH, granted.toArray(String[]::new));
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, RequestPostProcessor who)
            throws Exception {
        return mockMvc.perform(request.with(who)).andReturn().getResponse();
    }

    private MockHttpServletResponse submit(String path, Map<String, String> fields, RequestPostProcessor who)
            throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(csrf());
        fields.forEach(request::param);
        return perform(request, who);
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(perform(get(path), who).getContentAsString());
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private EntityData report(String name, long owner, boolean published) throws IOException {
        ManagedFile file = files.store(new byte[12], "annual.pdf", FileSchemes.PUBLIC, owner);
        EntityData blank = media.create(types.find("report").orElseThrow(), owner);
        Map<String, Object> values = new LinkedHashMap<>(blank.fields());
        values.put(MediaType.sourceFieldFor("report"), Map.of("target_id", file.id()));
        values.put(BaseFieldDefinition.STATUS, published);
        return media.save(new EntityData(MediaEntityType.ID, null, null, "report", name,
                EntityData.DEFAULT_LANGCODE, null, values));
    }

    @Test
    void theMediaTypesAreListedWithTheirSources() throws Exception {
        Document list = page(MediaTypeController.PATH, typeAdministrator());

        assertThat(list.selectFirst("tr[data-media-type=photo]").text()).contains("Photo").contains("Photographs.")
                .contains("Image");
        assertThat(list.selectFirst("tr[data-media-type=photo] a[href$=/fields]").attr("href"))
                .isEqualTo("/admin/structure/media/photo/fields");
        assertThat(perform(get(MediaTypeController.PATH), writer()).getStatus()).isEqualTo(403);
    }

    @Test
    void aTypeWhoseSourceIsGoneShowsTheSourcesId() throws Exception {
        types.save(new MediaType("legacy", "Legacy", "", "retired_source", "field_media_legacy"));

        assertThat(page(MediaTypeController.PATH, typeAdministrator()).selectFirst("tr[data-media-type=legacy]")
                .text()).contains("retired_source");
        assertThat(page(MediaTypeController.managePath("legacy"), typeAdministrator()).text())
                .contains("Media source: retired_source");
    }

    @Test
    void aTypeIsAddedWithTheSourceChosen() throws Exception {
        assertThat(page(MediaTypeController.PATH + "/add", typeAdministrator()).select("select[name=source] option"))
                .hasSizeGreaterThanOrEqualTo(5);

        MockHttpServletResponse response = submit(MediaTypeController.PATH + "/add", Map.of("label", "Brochure",
                "description", " Printed brochures. ", "source", DocumentSource.ID), typeAdministrator());

        assertThat(response.getRedirectedUrl()).isEqualTo(MediaTypeController.PATH);
        assertThat(types.find("brochure")).contains(new MediaType("brochure", "Brochure", "Printed brochures.",
                DocumentSource.ID, "field_media_brochure"));
    }

    @Test
    void aTypeNeedsANameAndASourceTheSiteHas() throws Exception {
        MockHttpServletResponse response = submit(MediaTypeController.PATH + "/add", Map.of("label", "",
                "source", "retired_source"), typeAdministrator());

        MockHttpServletResponse unchosen = submit(MediaTypeController.PATH + "/add", Map.of("label", "Brochure",
                "source", ""), typeAdministrator());

        assertThat(Jsoup.parse(response.getContentAsString()).text()).contains("This value is required.")
                .contains("Choose a media source.");
        assertThat(Jsoup.parse(unchosen.getContentAsString()).text()).contains("This value is required.")
                .doesNotContain("Choose a media source.");
    }

    @Test
    void aTypesNameAndDescriptionAreEditedAndItsSourceStays() throws Exception {
        assertThat(page(MediaTypeController.managePath("photo"), typeAdministrator()).text())
                .contains("Media source: Image");

        submit(MediaTypeController.managePath("photo"), Map.of("label", "Picture", "description", "Pictures."),
                typeAdministrator());
        MockHttpServletResponse invalid = submit(MediaTypeController.managePath("photo"), Map.of("label", ""),
                typeAdministrator());

        assertThat(types.find("photo")).map(MediaType::label).contains("Picture");
        assertThat(types.find("photo")).map(MediaType::source).contains(ImageSource.ID);
        assertThat(invalid.getContentAsString()).contains("This value is required.");
    }

    @Test
    void aTypeWithMediaIsKeptAndOneWithoutIsDeleted() throws Exception {
        report("Annual", EDITH, true);

        assertThat(page(MediaTypeController.managePath("report") + "/delete", typeAdministrator()).text())
                .contains("The site has media of this type.");
        submit(MediaTypeController.managePath("report") + "/delete", Map.of(), typeAdministrator());
        assertThat(types.find("report")).isPresent();

        assertThat(page(MediaTypeController.managePath("photo") + "/delete", typeAdministrator())
                .select("button[name=confirm]")).hasSize(1);
        submit(MediaTypeController.managePath("photo") + "/delete", Map.of(), typeAdministrator());
        assertThat(types.find("photo")).isEmpty();
        assertThat(perform(get(MediaTypeController.managePath("photo")), typeAdministrator()).getStatus())
                .isEqualTo(404);
    }

    @Test
    void addingMediaOffersTheTypesThePersonMayCreate() throws Exception {
        Document chooser = page(MediaController.ADD_PATH, as(EDITH, MediaPermissions.create("photo")));

        assertThat(chooser.select("a.list-group-item")).extracting(link -> link.attr("href"))
                .containsExactly(MediaController.ADD_PATH + "/photo");
        assertThat(perform(get(MediaController.ADD_PATH + "/report"), as(EDITH, MediaPermissions.create("photo")))
                .getStatus()).isEqualTo(403);
        assertThat(perform(get(MediaController.ADD_PATH + "/retired"), writer()).getStatus()).isEqualTo(404);
    }

    @Test
    void anImageIsAddedThroughTheFormWithAnUploadedFile() throws Exception {
        ManagedFile image = files.store(png(), "hall.png", FileSchemes.PUBLIC, EDITH);
        Document form = page(MediaController.ADD_PATH + "/photo", writer());
        assertThat(form.select("input[type=file][data-file-upload]")).hasSize(1);

        MockHttpServletResponse response = submit(MediaController.ADD_PATH + "/photo", Map.of(
                "name", "", "status", "true",
                "field_media_photo[0]:target_id", String.valueOf(image.id()),
                "field_media_photo[0]:alt", "The main hall"), writer());

        long id = Long.parseLong(response.getRedirectedUrl().substring("/media/".length()));
        EntityData saved = media.find(id).orElseThrow();
        assertThat(saved.label()).isEqualTo("hall.png");
        assertThat(saved.fields().get(MediaEntityType.THUMBNAIL)).isEqualTo(image.id());
        Document view = page(MediaEntityType.path(id), writer());
        assertThat(view.select("article[data-media=" + id + "] img")).hasSize(1);
        assertThat(view.select(".nav a")).extracting(link -> link.text()).contains("View", "Edit", "Delete");
    }

    @Test
    void aFormMissingTheSourceOrBreakingItsRulesComesBackWithTheErrors() throws Exception {
        MockHttpServletResponse missing = submit(MediaController.ADD_PATH + "/report", Map.of("name", "Annual"),
                writer());
        MockHttpServletResponse tooLong = submit(MediaController.ADD_PATH + "/report", Map.of(
                "name", "n".repeat(MediaService.MAX_NAME_LENGTH + 1)), writer());

        assertThat(Jsoup.parse(missing.getContentAsString()).text()).contains("This value is required.");
        assertThat(Jsoup.parse(tooLong.getContentAsString()).text()).contains("255");
        assertThat(queries.query(MediaEntityType.ID).count()).isZero();
    }

    @Test
    void aRemoteVideoTheSourceRefusesShowsTheReasonOnItsField() throws Exception {
        MockHttpServletResponse response = submit(MediaController.ADD_PATH + "/talk", Map.of(
                "field_media_talk", "https://example.com/video"), writer());

        assertThat(Jsoup.parse(response.getContentAsString()).text())
                .contains("The address is not from a site the media library embeds from.");
    }

    @Test
    void anItemIsEditedAndItsPublishedStateFollowsTheBox() throws Exception {
        EntityData saved = report("Annual", EDITH, true);
        long id = ((Number) saved.id()).longValue();
        Document editForm = page(MediaEntityType.editPath(id), writer());
        assertThat(editForm.select("input[name=new_revision]")).hasSize(1);
        assertThat(editForm.text()).contains("Embed code: <media-embed data-entity-uuid=\"" + saved.uuid()
                + "\" data-view-mode=\"default\"></media-embed>");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "Annual report");
        fields.put("new_revision", "true");
        fields.put("field_media_report[0]:target_id", String.valueOf(((Map<?, ?>) saved.fields()
                .get("field_media_report")).get("target_id")));
        submit(MediaEntityType.editPath(id), fields, writer());

        EntityData edited = media.find(id).orElseThrow();
        assertThat(edited.label()).isEqualTo("Annual report");
        assertThat(edited.fields().get(BaseFieldDefinition.STATUS)).isEqualTo(false);
        assertThat(edited.revisionId()).isNotEqualTo(saved.revisionId());
        fields.remove("new_revision");
        fields.put("name", "Annual report, final");
        submit(MediaEntityType.editPath(id), fields, writer());
        assertThat(media.find(id).orElseThrow().revisionId()).isEqualTo(edited.revisionId());
    }

    @Test
    void someoneElsesItemCannotBeEditedOrDeleted() throws Exception {
        long id = ((Number) report("Annual", EDITH + 1, true).id()).longValue();

        assertThat(perform(get(MediaEntityType.editPath(id)), writer()).getStatus()).isEqualTo(403);
        assertThat(perform(get(MediaEntityType.path(id) + "/delete"), writer()).getStatus()).isEqualTo(403);
        assertThat(page(MediaEntityType.path(id), writer()).select(".nav a")).extracting(link -> link.text())
                .containsExactly("View");
        assertThat(perform(get("/media/999999"), writer()).getStatus()).isEqualTo(404);
    }

    @Test
    void anItemIsDeletedThroughItsConfirmForm() throws Exception {
        long id = ((Number) report("Annual", EDITH, true).id()).longValue();

        assertThat(page(MediaEntityType.path(id) + "/delete", writer()).text())
                .contains("Delete the media item Annual?");
        MockHttpServletResponse response = submit(MediaEntityType.path(id) + "/delete", Map.of(), writer());

        assertThat(response.getRedirectedUrl()).isEqualTo(MediaController.OVERVIEW_PATH);
        assertThat(media.find(id)).isEmpty();
    }

    @Test
    void theOverviewListsMediaWithThumbnailsNewestFirst() throws Exception {
        EntityData older = report("Older", EDITH, false);
        EntityData newer = report("Newer", EDITH + 1, true);

        Document overview = page(MediaController.OVERVIEW_PATH, writer());

        assertThat(overview.select("tr[data-media]")).extracting(row -> row.attr("data-media"))
                .containsExactly(String.valueOf(newer.id()), String.valueOf(older.id()));
        assertThat(overview.selectFirst("tr[data-media=" + older.id() + "]").text()).contains("Unpublished")
                .contains("Report");
        assertThat(overview.selectFirst("tr[data-media=" + older.id() + "] img").attr("src"))
                .contains("/files/styles/thumbnail/public/");
        assertThat(overview.select("tr[data-media=" + newer.id() + "] a.btn")).isEmpty();
    }

    @Test
    void theOverviewShowsOriginalsWithoutTheThumbnailStyleAndNothingWithoutAThumbnail() throws Exception {
        EntityData item = report("Annual", EDITH, true);
        imageStyles.delete(MediaController.THUMBNAIL_STYLE);
        try {
            assertThat(page(MediaController.OVERVIEW_PATH, writer()).selectFirst("tr[data-media] img").attr("src"))
                    .doesNotContain("/styles/");
            Map<String, Object> values = new LinkedHashMap<>(item.fields());
            values.put(MediaEntityType.THUMBNAIL, 999_999L);
            media.save(item.withFields(values));
            assertThat(page(MediaController.OVERVIEW_PATH, writer()).select("tr[data-media] img")).hasSize(1);
        } finally {
            imageStyles.installDefaults();
        }
    }

    @Test
    void anOverviewItemWithoutAThumbnailShowsNone() throws Exception {
        EntityData blank = media.create(types.find("report").orElseThrow(), EDITH);
        Map<String, Object> values = new LinkedHashMap<>(blank.fields());
        values.put(MediaType.sourceFieldFor("report"), Map.of("target_id",
                files.store(new byte[1], "a.pdf", FileSchemes.PUBLIC, EDITH).id()));
        crud.save(new EntityData(MediaEntityType.ID, null, null, "report", "Bare", EntityData.DEFAULT_LANGCODE, null,
                values));

        assertThat(page(MediaController.OVERVIEW_PATH, writer()).select("tr[data-media] img")).isEmpty();
    }

    @Autowired
    private EntityCrudService crud;

    @Test
    void aRemoteVideoIsFramedThroughTheEmbedPage() throws Exception {
        oembed.describe(VIDEO, new OEmbedResource("video", "Opening <night>", "YouTube",
                "<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\"></iframe>", "", 480, 270));
        FormatterContext context = null;

        Document frame = Jsoup.parseBodyFragment(oembedFormatter.render(context, VIDEO));
        String address = frame.selectFirst("iframe").attr("src");
        MockHttpServletResponse embed = perform(get(URI.create(address)), writer());
        perform(get(URI.create(address)), writer());

        assertThat(address).startsWith(OEmbedEmbeds.PATH + "?url=");
        assertThat(embed.getContentAsString()).contains("<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\">")
                .contains("<title>Opening &lt;night&gt;</title>");
        assertThat(embed.getHeaders("Content-Security-Policy")).containsExactly(OEmbedController.POLICY);
        assertThat(oembed.fetches()).isEqualTo(1);
        assertThat(oembedFormatter.render(context, "https://example.com/<video>"))
                .isEqualTo("https://example.com/&lt;video&gt;");
    }

    @Test
    void theEmbedPageServesOnlySignedAddressesTheProviderCanDescribe() throws Exception {
        String unknown = "https://www.youtube.com/watch?v=unknownvid1";

        assertThat(perform(get(OEmbedEmbeds.PATH).param("url", VIDEO).param("hash", "forged"), writer())
                .getStatus()).isEqualTo(404);
        assertThat(perform(get(OEmbedEmbeds.PATH).param("url", VIDEO), writer()).getStatus()).isEqualTo(404);
        assertThat(perform(get(URI.create(embeds.frameAddress(unknown))), writer()).getStatus()).isEqualTo(404);
        assertThat(embeds.validHash(VIDEO, null)).isFalse();
        assertThat(oembedFormatter.id()).isEqualTo(OEmbedFormatter.ID);
    }

    @Test
    void aViewOfRenderedEntitiesDrawsMediaThroughTheirTemplate() throws IOException {
        report("Annual report", EDITH, true);
        ViewConfig view = new ViewConfig("media_rows", "Media rows", "", MediaEntityType.ID, List.of(new ViewDisplay(
                ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", new ViewOptions(List.of(), List.of(), List.of(),
                List.of(), List.of(), null, null, PluginConfig.of(EntityRow.ID), PluginConfig.of(NoneAccess.ID)),
                Map.of())));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AccountPrincipal(1L, "admin", "", true, List.of()), null, List.of()));
        try {
            Document drawn = Jsoup.parseBodyFragment(viewRenderer.render(view, ViewDisplay.DEFAULT, List.of(),
                    Map.of(), "/media-rows", false).html());

            assertThat(drawn.select(".views-row").text()).contains("Annual report");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
