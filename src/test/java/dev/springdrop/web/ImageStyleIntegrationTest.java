package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.image.EffectConfig;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.image.effects.DesaturateEffect;
import dev.springdrop.kernel.image.effects.ScaleEffect;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ImageStyleIntegrationTest extends AbstractIntegrationTest {

    private static final String STYLE = "gallery_thumb";

    private static final long UPLOADER = 63L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImageStyleManager styles;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private EntityQueryExecutor queries;

    @AfterEach
    void removeEverything() {
        styles.delete(STYLE);
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

    private ManagedFile stored(String scheme, byte[] content, String name) throws IOException {
        return files.store(new ByteArrayInputStream(content), name, content.length, scheme, UPLOADER);
    }

    private ImageStyle scaledTo(int width) {
        return new ImageStyle(STYLE, "Gallery thumbnail", List.of(new EffectConfig("scale", ScaleEffect.ID, 0,
                Map.of("width", width))));
    }

    private MockHttpServletResponse fetch(String url, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(get(url).with(who)).andReturn().getResponse();
    }

    private MockHttpServletResponse fetch(String url) throws Exception {
        return fetch(url, request -> request);
    }

    private static ImageSize sizeOf(MockHttpServletResponse response) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
        return new ImageSize(image.getWidth(), image.getHeight());
    }

    private static Path onDisk(String scheme, String path) {
        return FILES.resolve(scheme).resolve(path);
    }

    private static String unsigned(String url) {
        return url.substring(0, url.indexOf('?'));
    }

    @Test
    void aSiteStartsWithTheDefaultStyles() {
        assertThat(styles.all()).extracting(ImageStyle::id)
                .contains("thumbnail", "medium", "large", "wide", "square");
    }

    @Test
    void requestingAnImageInAStyleDrawsKeepsAndServesTheDerivative() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");

        MockHttpServletResponse response = fetch(styles.url(STYLE, file));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Cache-Control")).contains("public");
        assertThat(sizeOf(response)).isEqualTo(new ImageSize(40, 20));
        assertThat(Files.exists(onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, file)))).isTrue();
    }

    @Test
    void aDerivativeAlreadyDrawnIsServedWithoutAToken() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");
        fetch(styles.url(STYLE, file));

        assertThat(fetch(unsigned(styles.url(STYLE, file))).getStatus()).isEqualTo(200);
    }

    @Test
    void aDerivativeIsNotDrawnForAnAddressWithoutAValidToken() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");

        assertThat(fetch(unsigned(styles.url(STYLE, file))).getStatus()).isEqualTo(403);
        assertThat(fetch(unsigned(styles.url(STYLE, file)) + "?itok=forged").getStatus()).isEqualTo(403);
        assertThat(Files.exists(onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, file)))).isFalse();
    }

    @Test
    void editingTheStyleDrawsTheDerivativeAgain() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");
        fetch(styles.url(STYLE, file));

        styles.save(scaledTo(60));

        assertThat(Files.exists(onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, file)))).isFalse();
        assertThat(sizeOf(fetch(styles.url(STYLE, file)))).isEqualTo(new ImageSize(60, 30));
    }

    @Test
    void anOriginalNewerThanItsDerivativeIsDrawnAgain() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");
        fetch(styles.url(STYLE, file));
        Path derivative = onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, file));
        Files.setLastModifiedTime(derivative, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));

        assertThat(fetch(unsigned(styles.url(STYLE, file))).getStatus()).isEqualTo(403);
        assertThat(fetch(styles.url(STYLE, file)).getStatus()).isEqualTo(200);
        assertThat(Files.getLastModifiedTime(derivative).toInstant()).isAfter(Instant.parse("2020-01-02T00:00:00Z"));
    }

    @Test
    void deletingTheFileOrTheStyleRemovesItsDerivatives() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile kept = stored(FileSchemes.PUBLIC, png(200, 100), "kept.png");
        ManagedFile gone = stored(FileSchemes.PUBLIC, png(200, 100), "gone.png");
        fetch(styles.url(STYLE, kept));
        fetch(styles.url(STYLE, gone));

        files.delete(gone.id());
        assertThat(Files.exists(onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, gone)))).isFalse();
        assertThat(Files.exists(onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, kept)))).isTrue();

        styles.delete(STYLE);
        assertThat(Files.exists(onDisk(FileSchemes.PUBLIC, ImageStyleManager.STYLES_DIRECTORY + "/" + STYLE)))
                .isFalse();
    }

    @Test
    void aPrivateImagesDerivativeIsServedOnlyToWhoeverMayDownloadTheOriginal() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PRIVATE, png(200, 100), "plan.png");
        String url = styles.url(STYLE, file);

        MockHttpServletResponse owner = fetch(url, user(new AccountPrincipal(UPLOADER, "edith", "", true, List.of())));

        assertThat(url).startsWith(FileService.PRIVATE_URL_PREFIX + "/styles/" + STYLE + "/private/");
        assertThat(owner.getStatus()).isEqualTo(200);
        assertThat(owner.getHeader("Cache-Control")).contains("private");
        assertThat(fetch(url, user("visitor")).getStatus()).isEqualTo(403);
        assertThat(Files.exists(onDisk(FileSchemes.PRIVATE, ImageStyleManager.derivativePath(STYLE, file)))).isTrue();
    }

    @Test
    void aDerivativeOfNothingStoredOrOfAStyleTheSiteLacksIsNotFound() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");

        assertThat(fetch(styles.url(STYLE, file).replace("/" + STYLE + "/", "/retired/")).getStatus()).isEqualTo(404);
        assertThat(fetch("/files/styles/" + STYLE + "/public/2026-10/missing.png").getStatus()).isEqualTo(404);
        assertThat(fetch(styles.url(STYLE, file).replace("/files/", "/system/files/")).getStatus()).isEqualTo(404);
    }

    @Test
    void aFileThatIsNotAnImageHasNoDerivative() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, "plain text".getBytes(StandardCharsets.UTF_8), "notes.png");

        assertThat(fetch(styles.url(STYLE, file)).getStatus()).isEqualTo(404);
    }

    @Test
    void aStyleWorksOutTheSizeItDrawsSkippingEffectsTheSiteLacks() {
        ImageStyle style = new ImageStyle(STYLE, "Gallery thumbnail", List.of(
                new EffectConfig("scale", ScaleEffect.ID, 0, Map.of("width", 50)),
                new EffectConfig("gone", "retired_effect", 1, Map.of()),
                new EffectConfig("gray", DesaturateEffect.ID, 2, Map.of())));

        assertThat(styles.transformedSize(style, new ImageSize(200, 100))).contains(new ImageSize(50, 25));
    }

    @Test
    void aDrawnStyleSkipsEffectsTheSiteLacks() throws Exception {
        styles.save(new ImageStyle(STYLE, "Gallery thumbnail", List.of(
                new EffectConfig("gone", "retired_effect", 0, Map.of()),
                new EffectConfig("scale", ScaleEffect.ID, 1, Map.of("width", 50)))));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");

        assertThat(sizeOf(fetch(styles.url(STYLE, file)))).isEqualTo(new ImageSize(50, 25));
    }

    @Test
    void aDerivativeWhoseOriginalIsGoneFromDiskIsNotServedAsCurrent() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");
        fetch(styles.url(STYLE, file));
        Files.delete(onDisk(FileSchemes.PUBLIC, file.path()));

        assertThat(fetch(unsigned(styles.url(STYLE, file))).getStatus()).isEqualTo(403);
        assertThat(fetch(styles.url(STYLE, file)).getStatus()).isEqualTo(404);
    }

    @Test
    void derivativesThatCannotBeRemovedStopTheFlush() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");
        fetch(styles.url(STYLE, file));
        Path drawn = onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, file)).getParent();
        drawn.toFile().setWritable(false);
        try {
            assertThatThrownBy(() -> styles.flush(STYLE)).isInstanceOf(UncheckedIOException.class);
        } finally {
            drawn.toFile().setWritable(true);
        }
    }

    @Test
    void aDerivativeThatCannotBeRemovedStopsTheFileBeingDeleted() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");
        Path blocked = onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, file));
        Files.createDirectories(blocked.resolve("inside"));
        try {
            assertThatThrownBy(() -> files.delete(file.id())).isInstanceOf(UncheckedIOException.class);
        } finally {
            Files.delete(blocked.resolve("inside"));
            Files.delete(blocked);
        }
    }

    @Test
    void aStyleTheSiteNeverHadFlushesNothing() {
        styles.flush("never_saved");

        assertThat(styles.find("never_saved")).isEmpty();
    }

    @Test
    void anAdministratorCanFlushAStyleThroughItsConfirmForm() throws Exception {
        styles.save(scaledTo(40));
        ManagedFile file = stored(FileSchemes.PUBLIC, png(200, 100), "hall.png");
        fetch(styles.url(STYLE, file));

        mockMvc.perform(post(ImageStyleController.managePath(STYLE) + "/flush").with(csrf())
                .with(user("admin").authorities(new SimpleGrantedAuthority(
                        ImageStyleManager.ADMINISTER_IMAGE_STYLES))));

        assertThat(Files.exists(onDisk(FileSchemes.PUBLIC, ImageStyleManager.derivativePath(STYLE, file)))).isFalse();
    }
}
