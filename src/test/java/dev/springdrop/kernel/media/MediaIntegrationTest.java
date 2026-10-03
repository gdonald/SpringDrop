package dev.springdrop.kernel.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileFieldType;
import dev.springdrop.kernel.file.FileFormatter;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsage;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ImageFieldType;
import dev.springdrop.kernel.file.ImageFormatter;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.media.oembed.OEmbedFormatter;
import dev.springdrop.kernel.media.oembed.OEmbedResource;
import dev.springdrop.kernel.media.sources.AudioFileSource;
import dev.springdrop.kernel.media.sources.DocumentSource;
import dev.springdrop.kernel.media.sources.ImageSource;
import dev.springdrop.kernel.media.sources.RemoteVideoSource;
import dev.springdrop.kernel.media.sources.VideoFileSource;
import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@Import(MediaTestConfiguration.class)
class MediaIntegrationTest extends AbstractIntegrationTest {

    static final String VIDEO_ADDRESS = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    static final String THUMBNAIL_ADDRESS = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg";

    private static final long OWNER = 81L;

    @Autowired
    private MediaService media;

    @Autowired
    private MediaTypeManager types;

    @Autowired
    private MediaPermissions permissions;

    @Autowired
    private MediaIcons icons;

    @Autowired
    private StubOEmbedClient oembed;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private ViewDisplayManager displays;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private StateService state;

    @Autowired
    private RenderService renderer;

    @BeforeEach
    void aTypeOfEachSource() {
        media.createType("photo", "Photo", "Photographs.", ImageSource.ID);
        media.createType("report", "Report", "", DocumentSource.ID);
        media.createType("podcast", "Podcast", "", AudioFileSource.ID);
        media.createType("clip", "Clip", "", VideoFileSource.ID);
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
        List.of(MediaIcons.DOCUMENT, MediaIcons.AUDIO, MediaIcons.VIDEO)
                .forEach(kind -> state.remove(MediaIcons.STATE_COLLECTION, kind));
    }

    static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    private ManagedFile upload(String name, byte[] content) throws IOException {
        return files.store(new ByteArrayInputStream(content), name, content.length, FileSchemes.PUBLIC, OWNER);
    }

    private EntityData item(String type, String name, Object sourceValue) {
        EntityData blank = media.create(types.find(type).orElseThrow(), OWNER);
        Map<String, Object> values = new LinkedHashMap<>(blank.fields());
        values.put(MediaType.sourceFieldFor(type), sourceValue);
        return new EntityData(MediaEntityType.ID, null, null, type, name, EntityData.DEFAULT_LANGCODE, null, values);
    }

    private static Map<String, Object> fileValue(ManagedFile file) {
        return Map.of(FileItem.TARGET_ID, file.id());
    }

    private static Map<String, Object> imageValue(ManagedFile file) {
        return Map.of(FileItem.TARGET_ID, file.id(), FileItem.ALT, "The main hall");
    }

    private static long thumbnailOf(EntityData item) {
        return ((Number) item.fields().get(MediaEntityType.THUMBNAIL)).longValue();
    }

    private static OEmbedResource video(String title, String thumbnailUrl) {
        return new OEmbedResource("video", title, "YouTube", "<iframe src=\"https://www.youtube.com/embed/x\"></iframe>",
                thumbnailUrl, 480, 270);
    }

    @Test
    void eachTypeGetsARequiredSourceFieldOfItsSourcesKindShownWithItsSourcesFormatter() {
        assertThat(List.of("photo", "report", "talk")).extracting(type -> fields.findStorage(MediaEntityType.ID,
                MediaType.sourceFieldFor(type)).map(FieldStorageConfig::type).orElseThrow())
                .containsExactly(ImageFieldType.ID, FileFieldType.ID, "string");
        assertThat(fields.findInstance(MediaEntityType.ID, "photo", "field_media_photo").orElseThrow().required())
                .isTrue();
        assertThat(fields.findStorage(MediaEntityType.ID, "field_media_talk").orElseThrow().settings())
                .containsEntry(FieldSettings.MAX_LENGTH, RemoteVideoSource.MAX_ADDRESS_LENGTH);
        assertThat(List.of("photo", "report", "talk")).extracting(type -> displays.find(MediaEntityType.ID, type,
                ViewDisplayConfig.DEFAULT_MODE).orElseThrow().slots().get(MediaType.sourceFieldFor(type)).handler())
                .containsExactly(ImageFormatter.ID, FileFormatter.ID, OEmbedFormatter.ID);
        assertThat(types.find("photo")).map(MediaType::source).contains(ImageSource.ID);
    }

    @Test
    void anImageIsNamedAfterItsFileAndIsItsOwnThumbnail() throws IOException {
        ManagedFile image = upload("Main hall.png", png(40, 30));

        EntityData saved = media.save(item("photo", "", imageValue(image)));

        assertThat(saved.label()).isEqualTo("Main_hall.png");
        assertThat(thumbnailOf(saved)).isEqualTo(image.id());
        assertThat(usage.usages(image.id())).extracting(FileUsage::module).containsExactlyInAnyOrder("file", "media");
        assertThat(entities.load(MediaEntityType.ID, saved.id()).orElseThrow().fields()
                .get(MediaType.sourceFieldFor("photo"))).isNotNull();
    }

    @Test
    void documentsAudioAndVideoFilesShowTheIconOfTheirKind() throws IOException {
        EntityData report = media.save(item("report", "", fileValue(upload("annual.pdf", new byte[12]))));
        EntityData secondReport = media.save(item("report", "", fileValue(upload("budget.pdf", new byte[12]))));
        EntityData podcast = media.save(item("podcast", "Episode 1", fileValue(upload("one.mp3", new byte[12]))));
        EntityData clip = media.save(item("clip", "", fileValue(upload("tour.mp4", new byte[12]))));

        assertThat(report.label()).isEqualTo("annual.pdf");
        assertThat(podcast.label()).isEqualTo("Episode 1");
        assertThat(thumbnailOf(report)).isEqualTo(thumbnailOf(secondReport)).isEqualTo(icons.icon(MediaIcons.DOCUMENT));
        assertThat(List.of(thumbnailOf(podcast), thumbnailOf(clip)))
                .containsExactly(icons.icon(MediaIcons.AUDIO), icons.icon(MediaIcons.VIDEO));
        assertThat(files.find(icons.icon(MediaIcons.AUDIO))).map(ManagedFile::permanent).contains(true);
        assertThat(files.imageSize(files.find(thumbnailOf(clip)).orElseThrow())).isPresent();
    }

    @Test
    void anIconWhoseFileIsGoneIsDrawnAgain() throws IOException {
        long first = icons.icon(MediaIcons.DOCUMENT);
        usage.remove(first, MediaEntityType.ID, MediaIcons.USAGE_TYPE, MediaIcons.DOCUMENT, true);
        files.delete(first);
        upload("later.txt", new byte[1]);

        long drawnAgain = icons.icon(MediaIcons.DOCUMENT);

        assertThat(drawnAgain).isNotEqualTo(first);
        assertThat(files.find(drawnAgain)).map(ManagedFile::permanent).contains(true);
    }

    @Test
    void aRemoteVideoTakesItsProvidersTitleAndThumbnail() throws IOException {
        oembed.describe(VIDEO_ADDRESS, video("Opening night", THUMBNAIL_ADDRESS));
        oembed.serveThumbnail(THUMBNAIL_ADDRESS, png(48, 27));

        EntityData saved = media.save(item("talk", "", " " + VIDEO_ADDRESS + " "));

        ManagedFile thumbnail = files.find(thumbnailOf(saved)).orElseThrow();
        assertThat(saved.label()).isEqualTo("Opening night");
        assertThat(thumbnail.filename()).isEqualTo("remote-video.jpg");
        assertThat(thumbnail.permanent()).isTrue();
        assertThat(entities.load(MediaEntityType.ID, saved.id()).orElseThrow().fields()
                .get(MediaType.sourceFieldFor("talk"))).isEqualTo(" " + VIDEO_ADDRESS + " ");
    }

    @Test
    void aRemoteVideoThumbnailKeepsAnImageExtensionItsAddressNames() throws IOException {
        String pngThumbnail = "https://i.ytimg.com/vi/dQw4w9WgXcQ/still.PNG?size=large";
        oembed.describe(VIDEO_ADDRESS, video("", pngThumbnail));
        oembed.serveThumbnail(pngThumbnail, png(48, 27));

        EntityData saved = media.save(item("talk", "", VIDEO_ADDRESS));

        assertThat(files.find(thumbnailOf(saved)).orElseThrow().filename()).isEqualTo("remote-video.png");
        assertThat(saved.label()).isEqualTo(VIDEO_ADDRESS);
    }

    @Test
    void aRemoteVideoThumbnailWithoutAnExtensionIsStoredAsJpeg() throws IOException {
        String bareThumbnail = "https://i.ytimg.com/vi/dQw4w9WgXcQ/default";
        oembed.describe(VIDEO_ADDRESS, video("Opening night", bareThumbnail));
        oembed.serveThumbnail(bareThumbnail, png(48, 27));

        EntityData saved = media.save(item("talk", "", VIDEO_ADDRESS));

        assertThat(files.find(thumbnailOf(saved)).orElseThrow().filename()).isEqualTo("remote-video.jpg");
    }

    @Test
    void aNewItemGivenANameAndThumbnailStillReadsItsSource() throws IOException {
        ManagedFile file = upload("annual.pdf", new byte[12]);
        Map<String, Object> values = new LinkedHashMap<>(item("report", "Annual", fileValue(file)).fields());
        values.put(MediaEntityType.THUMBNAIL, file.id());

        EntityData saved = media.save(new EntityData(MediaEntityType.ID, null, null, "report", "Annual",
                EntityData.DEFAULT_LANGCODE, null, values));

        assertThat(thumbnailOf(saved)).isEqualTo(icons.icon(MediaIcons.DOCUMENT));
    }

    @Test
    void anItemStoredWithoutAThumbnailKeepsNoThumbnailInUse() throws IOException {
        EntityData stored = entities.save(item("report", "Annual", fileValue(upload("annual.pdf", new byte[12]))));

        assertThat(usage.filesUsedBy(MediaEntityType.ID, MediaEntityType.ID, stored.id())).isEmpty();
    }

    @Test
    void aRemoteVideoWithoutAThumbnailShowsTheVideoIcon() throws IOException {
        oembed.describe(VIDEO_ADDRESS, video("Opening night", ""));

        assertThat(thumbnailOf(media.save(item("talk", "", VIDEO_ADDRESS)))).isEqualTo(icons.icon(MediaIcons.VIDEO));
    }

    @Test
    void anAddressThatIsNotAVideoOrNotFromAProviderIsRefusedOnItsField() {
        oembed.describe(VIDEO_ADDRESS, new OEmbedResource("photo", "A still", "YouTube", "", "", 0, 0));

        assertThatThrownBy(() -> media.save(item("talk", "", VIDEO_ADDRESS)))
                .isInstanceOfSatisfying(EntityValidationException.class, invalid ->
                        assertThat(invalid.violations()).extracting(violation -> violation.propertyPath(),
                                violation -> violation.message())
                                .containsExactly(tuple("fields.field_media_talk", "The address is not a video.")));
        assertThatThrownBy(() -> media.save(item("talk", "", "https://example.com/video")))
                .isInstanceOfSatisfying(EntityValidationException.class, invalid ->
                        assertThat(invalid.violations().getFirst().message())
                                .isEqualTo("The address is not from a site the media library embeds from."));
    }

    @Test
    void anUploadedFileThatIsGoneCannotBeUsed() {
        assertThatThrownBy(() -> media.save(item("report", "", Map.of(FileItem.TARGET_ID, 999_999L))))
                .isInstanceOfSatisfying(EntityValidationException.class, invalid ->
                        assertThat(invalid.violations().getFirst().message())
                                .isEqualTo("Choose a file that has been uploaded."));
    }

    @Test
    void anItemWithoutASourceValueFailsOnItsRequiredField() {
        assertThatThrownBy(() -> media.save(item("report", "Empty", List.of())))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void theSourceIsReadAgainOnlyWhenTheValueChanges() throws IOException {
        oembed.describe(VIDEO_ADDRESS, video("Opening night", ""));
        EntityData saved = media.save(item("talk", "", VIDEO_ADDRESS));

        EntityData renamed = media.save(new EntityData(saved.entityType(), saved.id(), saved.uuid(), saved.bundle(),
                "Opening night, full", saved.langcode(), null, saved.fields()));
        String otherAddress = "https://vimeo.com/76979871";
        oembed.describe(otherAddress, video("Closing night", ""));
        Map<String, Object> changed = new LinkedHashMap<>(renamed.fields());
        changed.put(MediaType.sourceFieldFor("talk"), otherAddress);
        EntityData moved = media.save(renamed.withFields(changed));

        assertThat(oembed.fetches()).isEqualTo(2);
        assertThat(renamed.label()).isEqualTo("Opening night, full");
        assertThat(moved.label()).isEqualTo("Opening night, full");
    }

    @Test
    void aSavedItemWhoseRowIsGoneIsReadAgain() throws IOException {
        ManagedFile file = upload("annual.pdf", new byte[12]);
        EntityData unsaved = item("report", "Annual", fileValue(file));
        Map<String, Object> values = new LinkedHashMap<>(unsaved.fields());
        values.put(MediaEntityType.THUMBNAIL, 1L);

        EntityData saved = media.save(new EntityData(MediaEntityType.ID, 424_242L, null, "report", "Annual",
                EntityData.DEFAULT_LANGCODE, null, values));

        assertThat(thumbnailOf(saved)).isEqualTo(icons.icon(MediaIcons.DOCUMENT));
    }

    @Test
    void aNameLongerThanAMediaItemTakesIsCut() throws IOException {
        EntityData saved = media.save(item("report", "n".repeat(300), fileValue(upload("a.pdf", new byte[1]))));

        assertThat(saved.label()).hasSize(MediaService.MAX_NAME_LENGTH);
    }

    @Test
    void aNewThumbnailLeavesTheOlderRevisionsInUseUntilTheItemIsDeleted() throws IOException {
        ManagedFile first = upload("first.png", png(4, 4));
        ManagedFile second = upload("second.png", png(4, 4));
        EntityData saved = media.save(item("photo", "Hall", imageValue(first)));
        Map<String, Object> changed = new LinkedHashMap<>(saved.fields());
        changed.put(MediaType.sourceFieldFor("photo"), imageValue(second));

        EntityData updated = media.save(saved.withFields(changed));

        assertThat(thumbnailOf(updated)).isEqualTo(second.id());
        assertThat(usage.filesUsedBy(MediaEntityType.ID, MediaEntityType.ID, saved.id()))
                .containsExactlyInAnyOrder(first.id(), second.id());
        media.delete(((Number) saved.id()).longValue());
        assertThat(usage.filesUsedBy(MediaEntityType.ID, MediaEntityType.ID, saved.id())).isEmpty();
    }

    @Test
    void anItemOfATypeTheSiteDoesNotHaveIsRefused() {
        EntityData orphan = EntityData.of(MediaEntityType.ID, null, "retired", "Old", Map.of());

        assertThatThrownBy(() -> media.save(orphan)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aThumbnailThatCannotBeStoredFailsTheSave() throws IOException {
        oembed.describe(VIDEO_ADDRESS, video("Opening night", THUMBNAIL_ADDRESS));
        oembed.serveThumbnail(THUMBNAIL_ADDRESS, png(4, 4));
        java.io.File publicFiles = FILES.resolve("public").toFile();
        java.nio.file.Files.createDirectories(publicFiles.toPath());
        java.io.File[] months = publicFiles.listFiles();
        publicFiles.setWritable(false);
        for (java.io.File month : months) {
            month.setWritable(false);
        }
        try {
            assertThatThrownBy(() -> media.save(item("talk", "", VIDEO_ADDRESS)))
                    .isInstanceOf(UncheckedIOException.class);
        } finally {
            publicFiles.setWritable(true);
            for (java.io.File month : months) {
                month.setWritable(true);
            }
        }
    }

    @Test
    void mediaInUseKeepTheirTypeAndAreDrawnThroughTheirTemplate() throws IOException {
        EntityData saved = media.save(item("report", "Annual", fileValue(upload("annual.pdf", new byte[12]))));

        String drawn = renderer.render(media.build(saved, ViewDisplayConfig.FULL_MODE, false)).html();

        assertThat(media.inUse("report")).isTrue();
        assertThat(media.inUse("clip")).isFalse();
        assertThat(drawn).contains("data-media=\"" + saved.id() + "\"").contains("annual.pdf").contains("Annual");
    }

    @Test
    void anItemHoldingSeveralValuesIsReadFromTheFirst() {
        assertThat(MediaService.single(List.of("a", "b"))).isEqualTo("a");
        assertThat(MediaService.single(List.of())).isNull();
        assertThat(MediaService.single("a")).isEqualTo("a");
    }

    @Test
    void eachTypeOffersCreateEditAndDeletePermissions() {
        assertThat(permissions.permissions()).extracting(PermissionDefinition::name)
                .contains("create photo media", "edit own photo media", "edit any photo media",
                        "delete own photo media", "delete any photo media");
    }

    @Test
    void theSourcesDescribeThemselves() {
        assertThat(media.sources().ids()).contains(ImageSource.ID, DocumentSource.ID, AudioFileSource.ID,
                VideoFileSource.ID, RemoteVideoSource.ID);
        media.sources().ids().forEach(id -> {
            MediaSource source = media.sources().get(id);
            assertThat(List.of(source.id(), source.label(), source.description())).doesNotContain("");
        });
        assertThat(media.sources().get(DocumentSource.ID).sourceFieldInstanceSettings())
                .containsEntry(FileItem.FILE_EXTENSIONS, "txt rtf pdf doc docx odt xls xlsx ods ppt pptx odp csv");
        assertThat(media.sources().get(RemoteVideoSource.ID).sourceFieldInstanceSettings()).isEmpty();
        assertThat(media.sources().get(ImageSource.ID).sourceFieldStorageSettings())
                .containsEntry(FileItem.URI_SCHEME, FileSchemes.PUBLIC);
    }

    private static Authentication holding(long accountId, String... granted) {
        AccountPrincipal account = new AccountPrincipal(accountId, "edith", "", true, List.of(granted));
        return new UsernamePasswordAuthenticationToken(account, null,
                java.util.Arrays.stream(granted).map(SimpleGrantedAuthority::new).toList());
    }

    private boolean allowed(Object entity, String operation, Authentication who) {
        AccessResult result = entityTypeManager.accessHandlerFor(MediaEntityType.ID)
                .check(entityTypeManager.require(MediaEntityType.ID), entity, operation, who);
        return result.allowed();
    }

    @Test
    void accessGoesByTheTypeAndWhoOwnsTheItem() {
        Map<String, Object> publishedValues = Map.of(BaseFieldDefinition.STATUS, true, BaseFieldDefinition.OWNER, OWNER);
        EntityData published = EntityData.of(MediaEntityType.ID, 1L, "photo", "Hall", publishedValues);
        EntityData unpublished = EntityData.of(MediaEntityType.ID, 2L, "photo", "Draft",
                Map.of(BaseFieldDefinition.STATUS, false, BaseFieldDefinition.OWNER, OWNER));

        assertThat(allowed(published, EntityAccessHandler.VIEW, holding(9L, MediaPermissions.VIEW_MEDIA))).isTrue();
        assertThat(allowed(published, EntityAccessHandler.VIEW, holding(9L))).isFalse();
        assertThat(allowed(unpublished, EntityAccessHandler.VIEW,
                holding(OWNER, MediaPermissions.VIEW_OWN_UNPUBLISHED))).isTrue();
        assertThat(allowed(unpublished, EntityAccessHandler.VIEW,
                holding(9L, MediaPermissions.VIEW_OWN_UNPUBLISHED))).isFalse();
        assertThat(allowed(published, EntityAccessHandler.UPDATE, holding(OWNER, MediaPermissions.editOwn("photo"))))
                .isTrue();
        assertThat(allowed(published, EntityAccessHandler.UPDATE, holding(9L, MediaPermissions.editOwn("photo"))))
                .isFalse();
        assertThat(allowed(published, EntityAccessHandler.UPDATE, holding(9L, MediaPermissions.editAny("photo"))))
                .isTrue();
        assertThat(allowed(published, EntityAccessHandler.DELETE,
                holding(OWNER, MediaPermissions.deleteOwn("photo")))).isTrue();
        assertThat(allowed(published, EntityAccessHandler.DELETE, holding(9L, MediaPermissions.deleteAny("photo"))))
                .isTrue();
        assertThat(allowed(published, EntityAccessHandler.DELETE, holding(9L))).isFalse();
        assertThat(allowed(unpublished, EntityAccessHandler.VIEW, holding(OWNER))).isFalse();
        assertThat(allowed(published, EntityAccessHandler.UPDATE, holding(OWNER))).isFalse();
        assertThat(allowed(published, EntityAccessHandler.DELETE, holding(OWNER))).isFalse();
        assertThat(allowed("photo", EntityAccessHandler.CREATE, holding(9L, MediaPermissions.create("photo"))))
                .isTrue();
        assertThat(allowed("photo", EntityAccessHandler.CREATE, holding(9L))).isFalse();
        assertThat(allowed(published, EntityAccessHandler.CREATE, holding(9L))).isFalse();
        assertThat(allowed("photo", EntityAccessHandler.VIEW, holding(9L))).isFalse();
        assertThat(allowed(published, "publish", holding(9L))).isFalse();
        assertThat(allowed(unpublished, EntityAccessHandler.DELETE, holding(9L, MediaPermissions.ADMINISTER_MEDIA)))
                .isTrue();
        assertThat(allowed(published, EntityAccessHandler.VIEW, null)).isFalse();
        assertThat(allowed(EntityData.of(MediaEntityType.ID, 3L, "photo", "Ownerless",
                Map.of(BaseFieldDefinition.STATUS, false)), EntityAccessHandler.VIEW,
                holding(OWNER, MediaPermissions.VIEW_OWN_UNPUBLISHED))).isFalse();
        assertThat(allowed(unpublished, EntityAccessHandler.VIEW, new UsernamePasswordAuthenticationToken("anonymousUser",
                null, List.of(new SimpleGrantedAuthority(MediaPermissions.VIEW_OWN_UNPUBLISHED))))).isFalse();
    }
}
