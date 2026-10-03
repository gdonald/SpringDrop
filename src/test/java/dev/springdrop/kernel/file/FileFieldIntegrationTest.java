package dev.springdrop.kernel.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.validation.ConstraintViolation;
import dev.springdrop.kernel.validation.ValidationContext;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(FileFieldIntegrationTest.ListingTypes.class)
class FileFieldIntegrationTest extends AbstractIntegrationTest {

    record Listing(long id, String label) {
    }

    record ListingType(String id, String label) {
    }

    static final EntityType LISTING = EntityType.content("venue_listing", Listing.class)
            .withBundles("type", "venue_listing_type")
            .withRevisions();

    static final EntityType LISTING_TYPE = EntityType.config("venue_listing_type", ListingType.class);

    /** Notes keep no revisions, so clearing an attachment releases it whatever the save. */
    static final EntityType NOTE = EntityType.content("venue_note", Listing.class)
            .withBundles("type", "venue_note_type");

    static final EntityType NOTE_TYPE = EntityType.config("venue_note_type", ListingType.class);

    private static final String BUNDLE = "venue";

    private static final long UPLOADER = 7L;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

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

    @BeforeEach
    void aVenueWithAPhotoAndAFloorPlan() {
        entityTypeManager.installStorage(LISTING.id());
        bundles.save(LISTING.id(), new BundleDefinition(BUNDLE, "Venue"));
        fields.createStorage(new FieldStorageConfig("photo", LISTING.id(), ImageFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("photo", LISTING.id(), BUNDLE, "Photo").withSettings(Map.of(
                FileItem.FILE_EXTENSIONS, "png", FileItem.MAX_FILESIZE, 100_000L,
                FileItem.ALT_FIELD, true, FileItem.ALT_FIELD_REQUIRED, true,
                FileItem.MIN_RESOLUTION, "20x20", FileItem.MAX_RESOLUTION, "400x400")));
        fields.createStorage(new FieldStorageConfig("plan", LISTING.id(), FileFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("plan", LISTING.id(), BUNDLE, "Floor plan").withSettings(Map.of(
                FileItem.FILE_EXTENSIONS, "pdf txt", FileItem.MAX_FILESIZE, 64L)));
    }

    @AfterEach
    void removeEverything() {
        SecurityContextHolder.clearContext();
        queries.query(LISTING.id()).ids().forEach(id -> entities.delete(LISTING.id(), id));
        fields.fieldNames(LISTING.id(), BUNDLE).forEach(field -> fields.deleteStorage(LISTING.id(), field));
        bundles.delete(LISTING.id(), BUNDLE);
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

    private ManagedFile upload(String name, byte[] content, long owner) throws IOException {
        return files.store(new ByteArrayInputStream(content), name, content.length, FileSchemes.PUBLIC, owner);
    }

    private ManagedFile photo(int width, int height) throws IOException {
        return upload("hall.png", png(width, height), UPLOADER);
    }

    private static Map<String, Object> image(ManagedFile file, String alt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(FileItem.TARGET_ID, file.id());
        value.put(FileItem.ALT, alt);
        value.put(FileItem.WIDTH, 120);
        value.put(FileItem.HEIGHT, 80);
        return value;
    }

    private EntityData listing(Map<String, Object> values) {
        return new EntityData(LISTING.id(), null, null, BUNDLE, "Riverside hall", EntityData.DEFAULT_LANGCODE, null,
                values);
    }

    private List<String> violations(EntityData entity) {
        try {
            entities.save(entity);
            return List.of();
        } catch (EntityValidationException failure) {
            return failure.violations().stream().map(ConstraintViolation::message).toList();
        }
    }

    private static void signedInAs(long accountId) {
        AccountPrincipal account = new AccountPrincipal(accountId, "edith", "", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(account, null, account.getAuthorities()));
    }

    @Test
    void savingAnImageFieldRegistersUsageAndStoresAltAndDimensions() throws IOException {
        ManagedFile file = photo(120, 80);

        EntityData saved = entities.save(listing(Map.of("photo", image(file, "The main hall"))));

        assertThat(usage.usages(file.id())).extracting(FileUsage::module, FileUsage::type, FileUsage::id)
                .containsExactly(tuple(
                        FileUsageTracker.MODULE, LISTING.id(), String.valueOf(saved.id())));
        assertThat(files.find(file.id())).map(ManagedFile::permanent).contains(true);
        Object stored = entities.load(LISTING.id(), saved.id()).orElseThrow().fields().get("photo");
        assertThat(FileItem.fileId(stored)).contains(file.id());
        assertThat(List.of(FileItem.part(stored, FileItem.ALT), FileItem.part(stored, FileItem.WIDTH),
                FileItem.part(stored, FileItem.HEIGHT))).containsExactly("The main hall", "120", "80");
    }

    @Test
    void removingTheImageFromTheCurrentRevisionReleasesItsUsage() throws IOException {
        ManagedFile file = photo(120, 80);
        EntityData saved = entities.save(listing(Map.of("photo", image(file, "The main hall"))));

        entities.save(saved.withFields(Map.of("photo", List.of())), false);

        assertThat(usage.total(file.id())).isZero();
        assertThat(files.find(file.id())).map(ManagedFile::permanent).contains(false);
    }

    @Test
    void aFileAnOlderRevisionPointsAtStaysInUse() throws IOException {
        ManagedFile first = photo(120, 80);
        ManagedFile second = photo(200, 100);
        EntityData saved = entities.save(listing(Map.of("photo", image(first, "Before"))));

        entities.save(saved.withFields(Map.of("photo", image(second, "After"))));

        assertThat(usage.filesUsedBy(FileUsageTracker.MODULE, LISTING.id(), saved.id()))
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void deletingTheEntityReleasesEveryFileItUsed() throws IOException {
        ManagedFile file = photo(120, 80);
        EntityData saved = entities.save(listing(Map.of("photo", image(file, "The main hall"))));

        entities.delete(LISTING.id(), saved.id());

        assertThat(usage.total(file.id())).isZero();
    }

    @Test
    void changingAManagedFileItselfRecordsNoUsage() throws IOException {
        ManagedFile file = photo(120, 80);

        files.setPermanent(file.id(), true);

        assertThat(usage.total(file.id())).isZero();
    }

    @Test
    void aFileOfAnAllowedKindWithinTheSizeLimitIsAccepted() throws IOException {
        ManagedFile plan = upload("plan.txt", "Stage left".getBytes(StandardCharsets.UTF_8), UPLOADER);

        assertThat(violations(listing(Map.of("plan", Map.of(FileItem.TARGET_ID, plan.id(),
                FileItem.DESCRIPTION, "Ground floor"))))).isEmpty();
    }

    @Test
    void aFileWithAnExtensionTheFieldDoesNotAllowIsRejected() throws IOException {
        ManagedFile plan = upload("plan.csv", "a,b".getBytes(StandardCharsets.UTF_8), UPLOADER);

        assertThat(violations(listing(Map.of("plan", Map.of(FileItem.TARGET_ID, plan.id())))))
                .containsExactly("Only files with these extensions are allowed: pdf txt.");
    }

    @Test
    void aFileLargerThanTheFieldAllowsIsRejected() throws IOException {
        ManagedFile plan = upload("plan.txt", new byte[65], UPLOADER);

        assertThat(violations(listing(Map.of("plan", Map.of(FileItem.TARGET_ID, plan.id())))))
                .containsExactly("The file is larger than 64 bytes.");
    }

    @Test
    void aValueThatPointsAtNoStoredFileIsRejected() {
        assertThat(violations(listing(Map.of("plan", Map.of(FileItem.TARGET_ID, 999_999L)))))
                .containsExactly("Choose a file that has been uploaded.");
        assertThat(violations(listing(Map.of("plan", "plan.txt"))))
                .containsExactly("Choose a file that has been uploaded.");
    }

    @Test
    void aFileThatIsNotAnImageIsRejectedByAnImageField() throws IOException {
        ManagedFile fake = upload("hall.png", "not a picture".getBytes(StandardCharsets.UTF_8), UPLOADER);

        assertThat(violations(listing(Map.of("photo", image(fake, "The main hall")))))
                .containsExactly("The file is not an image.");
    }

    @Test
    void anImageOutsideThePixelBoundsIsRejected() throws IOException {
        assertThat(violations(listing(Map.of("photo", image(photo(10, 300), "Narrow")))))
                .containsExactly("The image is smaller than 20x20 pixels.");
        assertThat(violations(listing(Map.of("photo", image(photo(500, 300), "Wide")))))
                .containsExactly("The image is larger than 400x400 pixels.");
    }

    @Test
    void aDescriptionLongerThanAFileTakesIsRejected() throws IOException {
        ManagedFile plan = upload("plan.txt", "Stage left".getBytes(StandardCharsets.UTF_8), UPLOADER);

        assertThat(violations(listing(Map.of("plan", Map.of(FileItem.TARGET_ID, plan.id(),
                FileItem.DESCRIPTION, "d".repeat(FileItem.MAX_DESCRIPTION_LENGTH + 1))))))
                .containsExactly("The description is longer than 255 characters.");
    }

    @Test
    void alternativeTextOrATitleLongerThanAnImageTakesIsRejected() throws IOException {
        String tooLong = "a".repeat(FileItem.MAX_IMAGE_TEXT_LENGTH + 1);
        Map<String, Object> longTitle = image(photo(120, 80), "The main hall");
        longTitle.put(FileItem.TITLE, tooLong);

        assertThat(violations(listing(Map.of("photo", image(photo(120, 80), tooLong)))))
                .containsExactly("Alternative text and titles are at most 512 characters.");
        assertThat(violations(listing(Map.of("photo", longTitle))))
                .containsExactly("Alternative text and titles are at most 512 characters.");
    }

    @Test
    void anImageWithoutRequiredAlternativeTextIsRejected() throws IOException {
        assertThat(violations(listing(Map.of("photo", image(photo(120, 80), " ")))))
                .containsExactly("Alternative text is required.");
    }

    @Autowired
    private FileItemConstraint constraint;

    @Test
    void aNoteThatKeepsNoRevisionsReleasesAnAttachmentOnceItIsCleared() throws IOException {
        entityTypeManager.installStorage(NOTE.id());
        bundles.save(NOTE.id(), new BundleDefinition(BUNDLE, "Venue"));
        fields.createStorage(new FieldStorageConfig("attachment", NOTE.id(), FileFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("attachment", NOTE.id(), BUNDLE, "Attachment"));
        ManagedFile file = upload("hours.txt", "Open daily".getBytes(StandardCharsets.UTF_8), UPLOADER);
        try {
            EntityData note = entities.save(new EntityData(NOTE.id(), null, null, BUNDLE, "Hours",
                    EntityData.DEFAULT_LANGCODE, null, Map.of("attachment", Map.of(FileItem.TARGET_ID, file.id()))));
            assertThat(usage.total(file.id())).isOne();

            entities.save(note.withFields(Map.of("attachment", List.of())));

            assertThat(usage.total(file.id())).isZero();
        } finally {
            queries.query(NOTE.id()).ids().forEach(id -> entities.delete(NOTE.id(), id));
            fields.deleteStorage(NOTE.id(), "attachment");
            bundles.delete(NOTE.id(), BUNDLE);
        }
    }

    @Test
    void aFieldWithNoLimitsTakesAFileOfAnyKindAndSize() throws IOException {
        ManagedFile file = upload("notes.md", new byte[4096], UPLOADER);

        assertThat(constraint.validate(Map.of(FileItem.TARGET_ID, file.id()), Map.of(),
                ValidationContext.of(null))).isEmpty();
    }

    @Test
    void anImageFieldWithNoPixelBoundsOrAlternativeTextRequirementTakesAnyReadableImage() throws IOException {
        ManagedFile file = photo(3, 3);

        assertThat(constraint.validate(Map.of(FileItem.TARGET_ID, file.id()), Map.of(FileItem.IMAGE, true),
                ValidationContext.of(null))).isEmpty();
    }

    @Test
    void anImageWhoseContentIsGoneIsNotAnImage() throws IOException {
        ManagedFile file = photo(120, 80);
        Files.delete(FILES.resolve("public").resolve(file.path()));

        assertThat(files.imageSize(file)).isEmpty();
    }

    @Autowired
    private FileFormatter fileFormatter;

    @Autowired
    private ImageFormatter imageFormatter;

    private FormatterContext context() {
        return new FormatterContext(fields.findStorage(LISTING.id(), "plan").orElseThrow(),
                fields.findInstance(LISTING.id(), BUNDLE, "plan").orElseThrow(), Map.of());
    }

    @Test
    void aFileIsShownAsALinkByItsDescriptionOrItsName() throws IOException {
        ManagedFile plan = upload("plan.txt", "Stage left".getBytes(StandardCharsets.UTF_8), UPLOADER);

        Document described = Jsoup.parseBodyFragment(fileFormatter.render(context(),
                Map.of(FileItem.TARGET_ID, plan.id(), FileItem.DESCRIPTION, "Ground <floor>")));
        Document named = Jsoup.parseBodyFragment(fileFormatter.render(context(), Map.of(FileItem.TARGET_ID, plan.id())));

        assertThat(described.selectFirst("a").text()).isEqualTo("Ground <floor>");
        assertThat(described.selectFirst("a").attr("href")).isEqualTo(files.url(plan));
        assertThat(described.selectFirst("a").attr("type")).isEqualTo("text/plain");
        assertThat(described.text()).endsWith("(10 bytes)");
        assertThat(named.selectFirst("a").text()).isEqualTo("plan.txt");
    }

    @Test
    void aFileMarkedNotToBeListedOrNoLongerStoredShowsNothing() throws IOException {
        ManagedFile plan = upload("plan.txt", "Stage left".getBytes(StandardCharsets.UTF_8), UPLOADER);

        assertThat(fileFormatter.render(context(), Map.of(FileItem.TARGET_ID, plan.id(), FileItem.DISPLAY, false)))
                .isEmpty();
        assertThat(fileFormatter.render(context(), Map.of(FileItem.TARGET_ID, 999_999L))).isEmpty();
        assertThat(imageFormatter.render(context(), Map.of(FileItem.TARGET_ID, 999_999L))).isEmpty();
    }

    @Test
    void anImageIsShownWithItsAlternativeTextTitleAndSizeAndLoadsLazily() throws IOException {
        ManagedFile file = photo(120, 80);
        Map<String, Object> value = new LinkedHashMap<>(image(file, "The \"main\" hall"));
        value.put(FileItem.TITLE, "Seats 200");

        Element img = Jsoup.parseBodyFragment(imageFormatter.render(context(), value)).selectFirst("img");

        assertThat(img.attr("src")).isEqualTo(files.url(file));
        assertThat(img.attr("alt")).isEqualTo("The \"main\" hall");
        assertThat(img.attr("title")).isEqualTo("Seats 200");
        assertThat(List.of(img.attr("width"), img.attr("height"), img.attr("loading")))
                .containsExactly("120", "80", "lazy");
    }

    @Test
    void anImageWithoutATitleOrKnownSizeLeavesThoseAttributesOff() throws IOException {
        ManagedFile file = photo(120, 80);

        Element img = Jsoup.parseBodyFragment(imageFormatter.render(context(),
                Map.of(FileItem.TARGET_ID, file.id(), FileItem.WIDTH, "wide"))).selectFirst("img");

        assertThat(img.hasAttr("title")).isFalse();
        assertThat(img.hasAttr("width")).isFalse();
        assertThat(img.hasAttr("height")).isFalse();
    }

    @Nested
    class SavedInARequest {

        @Test
        void aTemporaryUploadOfThePersonSavingIsAccepted() throws IOException {
            ManagedFile file = photo(120, 80);
            signedInAs(UPLOADER);

            assertThat(violations(listing(Map.of("photo", image(file, "The main hall"))))).isEmpty();
        }

        @Test
        void someoneElsesTemporaryUploadIsRejected() throws IOException {
            ManagedFile file = photo(120, 80);
            signedInAs(UPLOADER + 1);

            assertThat(violations(listing(Map.of("photo", image(file, "The main hall")))))
                    .containsExactly("You may not use this file.");
        }

        @Test
        void anUploadMadeWithoutSigningInBelongsToEveryoneNotSignedIn() throws IOException {
            ManagedFile file = upload("hall.png", png(120, 80), FileItemConstraint.ANONYMOUS_OWNER);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

            assertThat(violations(listing(Map.of("photo", image(file, "The main hall"))))).isEmpty();
        }

        @Test
        void aFileTheEntityAlreadyUsesCanBeKeptOnTheNextSave() throws IOException {
            ManagedFile file = photo(120, 80);
            EntityData saved = entities.save(listing(Map.of("photo", image(file, "The main hall"))));
            signedInAs(UPLOADER + 1);

            assertThat(violations(saved.withFields(Map.of("photo", image(file, "The hall at night"))))).isEmpty();
        }

        @Test
        void aFileCannotBeAttachedToSomethingThatIsNotAnEntity() throws IOException {
            ManagedFile file = photo(120, 80);
            files.setPermanent(file.id(), true);
            signedInAs(UPLOADER);

            assertThat(constraint.validate(Map.of(FileItem.TARGET_ID, file.id()), Map.of(),
                    ValidationContext.of("a draft"))).contains("You may not use this file.");
        }

        @Test
        void aFileAnotherEntityUsesCannotBeAttached() throws IOException {
            ManagedFile file = photo(120, 80);
            EntityData other = entities.save(listing(Map.of("photo", image(file, "The main hall"))));
            signedInAs(UPLOADER);

            assertThat(violations(listing(Map.of("photo", image(file, "Borrowed")))))
                    .containsExactly("You may not use this file.");
            assertThat(violations(new EntityData(LISTING.id(), ((Number) other.id()).longValue() + 1, null, BUNDLE, "Annex",
                    EntityData.DEFAULT_LANGCODE, null, Map.of("photo", image(file, "Borrowed")))))
                    .containsExactly("You may not use this file.");
        }
    }

    @TestConfiguration
    static class ListingTypes {

        @Bean
        EntityTypeProvider listingTypes() {
            return () -> List.of(LISTING, LISTING_TYPE, NOTE, NOTE_TYPE);
        }
    }
}
