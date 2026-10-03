package dev.springdrop.kernel.media;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.TextLongFieldType;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileItemConstraint;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.media.sources.DocumentSource;
import dev.springdrop.kernel.media.sources.ImageSource;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(MediaTestConfiguration.class)
class MediaEmbedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MediaService media;

    @Autowired
    private MediaTypeManager types;

    @Autowired
    private TextFormatManager formats;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private PluginRegistry registry;

    @Autowired
    private StateService state;

    @BeforeEach
    void photosThatCanBeViewed() {
        media.createType("photo", "Photo", "", ImageSource.ID);
        media.createType("report", "Report", "", DocumentSource.ID);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null,
                List.of(new SimpleGrantedAuthority(MediaPermissions.VIEW_MEDIA))));
    }

    @AfterEach
    void removeEverything() {
        SecurityContextHolder.clearContext();
        queries.query(MediaEntityType.ID).ids().forEach(id -> media.delete(((Number) id).longValue()));
        types.all().forEach(media::deleteType);
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
        state.remove(MediaIcons.STATE_COLLECTION, MediaIcons.DOCUMENT);
    }

    private EntityData photo(String name, boolean published) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", bytes);
        ManagedFile image = files.store(bytes.toByteArray(), name + ".png", FileSchemes.PUBLIC,
                FileItemConstraint.ANONYMOUS_OWNER);
        EntityData blank = media.create(types.find("photo").orElseThrow(), 5L);
        Map<String, Object> values = new LinkedHashMap<>(blank.fields());
        values.put(MediaType.sourceFieldFor("photo"), Map.of(FileItem.TARGET_ID, image.id(), FileItem.ALT, name));
        values.put(BaseFieldDefinition.STATUS, published);
        return media.save(new EntityData(MediaEntityType.ID, null, null, "photo", name, EntityData.DEFAULT_LANGCODE,
                null, values));
    }

    private Document processed(String text, String format) {
        return Jsoup.parseBodyFragment(formats.process(text, format));
    }

    @Test
    void anEmbeddedMediaItemRendersAndSurvivesTheSanitizer() throws IOException {
        EntityData hall = photo("Main hall", true);

        Document body = processed("<p>Our hall:</p>" + MediaEmbedFilter.embedCode(hall.uuid())
                + "<script>alert(1)</script>", TextFormatManager.BASIC_HTML);

        assertThat(body.select("article[data-media=" + hall.id() + "] img")).hasSize(1);
        assertThat(body.selectFirst("article[data-media]").hasClass("media--default")).isTrue();
        assertThat(body.select("script")).isEmpty();
        assertThat(body.text()).contains("Our hall:").doesNotContain("media-embed");
    }

    @Test
    void anEmbedNamesItsViewModeAndUuidInEitherCase() throws IOException {
        EntityData hall = photo("Main hall", true);

        Document body = processed("<MEDIA-EMBED data-view-mode='teaser' data-entity-uuid='"
                + hall.uuid().toString().toUpperCase() + "'>", TextFormatManager.FULL_HTML);

        assertThat(body.selectFirst("article[data-media]").hasClass("media--teaser")).isTrue();
    }

    @Test
    void anEmbedWithoutAViewModeIsDrawnInTheDefaultOne() throws IOException {
        EntityData hall = photo("Main hall", true);

        Document body = processed("<media-embed data-entity-uuid=\"" + hall.uuid() + "\"></media-embed>",
                TextFormatManager.BASIC_HTML);

        assertThat(body.selectFirst("article[data-media]").hasClass("media--default")).isTrue();
    }

    @Test
    void anEmbedOfSomethingGoneOrNotViewableOrWithoutAUuidDrawsNothing() throws IOException {
        EntityData draft = photo("Draft", false);

        Document body = processed(MediaEmbedFilter.embedCode(draft.uuid())
                + MediaEmbedFilter.embedCode(UUID.randomUUID())
                + "<media-embed data-view-mode=\"default\"></media-embed><p>after</p>", TextFormatManager.BASIC_HTML);

        assertThat(body.select("article")).isEmpty();
        assertThat(body.text()).isEqualTo("after");
    }

    @Test
    void aMarkerTypedIntoTheTextIsNotDrawn() throws IOException {
        EntityData hall = photo("Main hall", true);
        String guessed = "[media-embed-00000000000000000000000000000000:" + hall.uuid() + ":default]";

        assertThat(processed(guessed, TextFormatManager.BASIC_HTML).select("article")).isEmpty();
    }

    @Test
    void aFormatWithoutTheFilterDropsTheEmbed() throws IOException {
        EntityData hall = photo("Main hall", true);

        assertThat(processed(MediaEmbedFilter.embedCode(hall.uuid()), TextFormatManager.RESTRICTED_HTML)
                .select("article")).isEmpty();
    }

    @Test
    void mediaEmbeddingItselfIsDrawnOnlyAFewLevelsDeep() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("writer", null,
                List.of(new SimpleGrantedAuthority(MediaPermissions.VIEW_MEDIA),
                        new SimpleGrantedAuthority(TextFormatManager.permission(TextFormatManager.BASIC_HTML)))));
        fields.createStorage(new FieldStorageConfig("field_caption", MediaEntityType.ID, TextLongFieldType.ID, 1,
                Map.of()));
        fields.createInstance(FieldInstanceConfig.of("field_caption", MediaEntityType.ID, "photo", "Caption"));
        EntityData hall = photo("Main hall", true);
        Map<String, Object> values = new LinkedHashMap<>(hall.fields());
        values.put("field_caption", Map.of(FormattedText.VALUE, MediaEmbedFilter.embedCode(hall.uuid()),
                FormattedText.FORMAT, TextFormatManager.BASIC_HTML));
        media.save(hall.withFields(values));

        Document body = processed(MediaEmbedFilter.embedCode(hall.uuid()), TextFormatManager.BASIC_HTML);

        assertThat(body.select("article[data-media]")).hasSize(MediaEmbedFilter.MAX_DEPTH);
    }

    @Test
    void anEditorKeepsEmbedsInFormatsThatDrawThem() {
        assertThat(formats.editorTags(formats.find(TextFormatManager.BASIC_HTML).orElseThrow()))
                .contains(TextFormatManager.MEDIA_EMBED_TAG);
        assertThat(formats.editorTags(formats.find(TextFormatManager.RESTRICTED_HTML).orElseThrow()))
                .doesNotContain(TextFormatManager.MEDIA_EMBED_TAG);
        assertThat(registry.managerFor(TextFilter.class).get(TextFormatManager.MEDIA_EMBED_FILTER).label())
                .isEqualTo("Embed media");
    }
}
