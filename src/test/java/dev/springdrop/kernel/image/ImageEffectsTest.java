package dev.springdrop.kernel.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.image.effects.CropEffect;
import dev.springdrop.kernel.image.effects.DesaturateEffect;
import dev.springdrop.kernel.image.effects.ResizeEffect;
import dev.springdrop.kernel.image.effects.RotateEffect;
import dev.springdrop.kernel.image.effects.ScaleAndCropEffect;
import dev.springdrop.kernel.image.effects.ScaleEffect;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ImageEffectsTest {

    private static final Java2dToolkit TOOLKIT = new Java2dToolkit();

    private static ToolkitImage image(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return new Java2dToolkit.Java2dImage(image);
    }

    private static int pixel(ToolkitImage image, int x, int y) {
        return ((Java2dToolkit.Java2dImage) image).image().getRGB(x, y);
    }

    private static List<Integer> size(ToolkitImage image) {
        return List.of(image.width(), image.height());
    }

    private static String name(String setting) {
        return ImageEffect.SETTINGS_PREFIX + setting;
    }

    @Nested
    class Toolkit {

        @Test
        void anImageSavesAndLoadsInEachFormatTheToolkitWrites() throws IOException {
            for (String format : List.of("png", "jpg", "JPEG", "gif")) {
                ByteArrayOutputStream saved = new ByteArrayOutputStream();
                TOOLKIT.save(image(30, 20, Color.RED), format, saved);

                assertThat(size(TOOLKIT.load(new ByteArrayInputStream(saved.toByteArray())))).containsExactly(30, 20);
            }
        }

        @Test
        void contentThatIsNotAnImageDoesNotLoad() {
            assertThatThrownBy(() -> TOOLKIT.load(new ByteArrayInputStream("plain text".getBytes())))
                    .isInstanceOf(IOException.class);
        }

        @Test
        void aFormatTheToolkitCannotWriteIsRefused() {
            assertThatThrownBy(() -> TOOLKIT.save(image(3, 3, Color.RED), "webp", new ByteArrayOutputStream()))
                    .isInstanceOf(IOException.class);
        }

        @Test
        void aJpegIsDrawnOverWhite() throws IOException {
            ByteArrayOutputStream saved = new ByteArrayOutputStream();
            TOOLKIT.save(image(4, 4, new Color(0, 0, 0, 0)), "jpg", saved);

            BufferedImage loaded = ImageIO.read(new ByteArrayInputStream(saved.toByteArray()));
            assertThat(new Color(loaded.getRGB(1, 1)).getRed()).isGreaterThan(240);
        }
    }

    @Nested
    class Scale {

        private final ScaleEffect effect = new ScaleEffect();

        @Test
        void anImageScalesToFitInsideTheBoxKeepingItsProportions() {
            ToolkitImage scaled = effect.apply(TOOLKIT, image(400, 200, Color.RED), Map.of("width", 100, "height", 100));

            assertThat(size(scaled)).containsExactly(100, 50);
        }

        @Test
        void aSingleSideBoundsTheImageByThatSideAlone() {
            assertThat(effect.transformedSize(new ImageSize(400, 200), Map.of("width", 200)))
                    .contains(new ImageSize(200, 100));
            assertThat(effect.transformedSize(new ImageSize(400, 200), Map.of("height", 50)))
                    .contains(new ImageSize(100, 50));
        }

        @Test
        void aSmallerImageIsLeftAsItIsUnlessUpscalingIsAllowed() {
            ToolkitImage small = image(50, 25, Color.RED);

            assertThat(effect.apply(TOOLKIT, small, Map.of("width", 100))).isSameAs(small);
            assertThat(size(effect.apply(TOOLKIT, small, Map.of("width", 100, ScaleEffect.UPSCALE, true))))
                    .containsExactly(100, 50);
        }

        @Test
        void anImageWhoseWidthRoundsToTheSameStillScalesItsHeight() {
            assertThat(size(effect.apply(TOOLKIT, image(100, 1000, Color.RED), Map.of("height", 999))))
                    .containsExactly(100, 999);
        }

        @Test
        void noBoundsLeaveTheImageAsItIs() {
            assertThat(effect.transformedSize(new ImageSize(40, 20), Map.of())).contains(new ImageSize(40, 20));
        }

        @Test
        void theSummaryNamesTheBoxAndUpscaling() {
            assertThat(effect.summary(Map.of("width", 100))).isEqualTo("100 by any height");
            assertThat(effect.summary(Map.of("height", 80, ScaleEffect.UPSCALE, true)))
                    .isEqualTo("any width by 80, upscaling allowed");
            assertThat(effect.label()).isEqualTo("Scale");
            assertThat(effect.id()).isEqualTo(ScaleEffect.ID);
        }

        @Test
        void eachSideIsRequiredOnlyWhenTheOtherIsBlank() {
            List<FormElement> form = effect.settingsForm(Map.of("width", 100, ScaleEffect.UPSCALE, true));

            assertThat(form).extracting(FormElement::name)
                    .containsExactly(name("width"), name("height"), name(ScaleEffect.UPSCALE));
            assertThat(form).extracting(FormElement::value).containsExactly("100", "", true);
            assertThat(form.get(0).states()).extracting(state -> state.dependsOn()).containsExactly(name("height"));
        }

        @Test
        void aSubmissionKeepsTheSidesGivenAndWhetherUpscalingIsAllowed() {
            assertThat(effect.settingsValues(Map.of(name("width"), "120", name("height"), " ",
                    name(ScaleEffect.UPSCALE), FormRenderer.CHECKED_VALUE)))
                    .isEqualTo(Map.of("width", 120, ScaleEffect.UPSCALE, true));
            assertThat(effect.settingsValues(Map.of(name("height"), "90")))
                    .isEqualTo(Map.of("height", 90, ScaleEffect.UPSCALE, false));
        }
    }

    @Nested
    class ScaleAndCrop {

        private final ScaleAndCropEffect effect = new ScaleAndCropEffect();

        @Test
        void anImageIsScaledToCoverTheBoxAndCroppedToItExactly() {
            ToolkitImage wide = effect.apply(TOOLKIT, image(400, 100, Color.RED), Map.of("width", 50, "height", 50));
            ToolkitImage tall = effect.apply(TOOLKIT, image(100, 400, Color.RED), Map.of("width", 60, "height", 30));

            assertThat(size(wide)).containsExactly(50, 50);
            assertThat(size(tall)).containsExactly(60, 30);
            assertThat(effect.transformedSize(new ImageSize(1, 1), Map.of("width", 50, "height", 40)))
                    .contains(new ImageSize(50, 40));
        }

        @Test
        void itIsDescribedByItsSize() {
            assertThat(effect.summary(Map.of("width", 50, "height", 40))).isEqualTo("50 by 40");
            assertThat(effect.label()).isEqualTo("Scale and crop");
            assertThat(effect.id()).isEqualTo(ScaleAndCropEffect.ID);
            assertThat(effect.settingsForm(Map.of())).extracting(FormElement::required).containsExactly(true, true);
            assertThat(effect.settingsValues(Map.of(name("width"), "50", name("height"), "nope")))
                    .isEqualTo(Map.of("width", 50, "height", 1));
        }
    }

    @Nested
    class Resize {

        private final ResizeEffect effect = new ResizeEffect();

        @Test
        void anImageIsStretchedToTheExactSize() {
            assertThat(size(effect.apply(TOOLKIT, image(40, 10, Color.RED), Map.of("width", 20, "height", 30))))
                    .containsExactly(20, 30);
            assertThat(effect.transformedSize(new ImageSize(40, 10), Map.of("width", 20, "height", 30)))
                    .contains(new ImageSize(20, 30));
            assertThat(effect.summary(Map.of("width", 20, "height", 30))).isEqualTo("20 by 30");
            assertThat(List.of(effect.id(), effect.label())).containsExactly(ResizeEffect.ID, "Resize");
            assertThat(effect.settingsValues(Map.of(name("width"), "20", name("height"), "30")))
                    .isEqualTo(Map.of("width", 20, "height", 30));
            assertThat(effect.settingsForm(Map.of("width", 20))).extracting(FormElement::value)
                    .containsExactly("20", "");
        }
    }

    @Nested
    class Crop {

        private final CropEffect effect = new CropEffect();

        /** Left half red, right half blue, so where the crop came from shows in its color. */
        private ToolkitImage halves() {
            BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) {
                    image.setRGB(x, y, (x < 20 ? Color.RED : Color.BLUE).getRGB());
                }
            }
            return new Java2dToolkit.Java2dImage(image);
        }

        @Test
        void theCropIsTakenFromTheAnchor() {
            ToolkitImage left = effect.apply(TOOLKIT, halves(), Map.of("width", 10, "height", 10,
                    CropEffect.ANCHOR, "left-top"));
            ToolkitImage right = effect.apply(TOOLKIT, halves(), Map.of("width", 10, "height", 10,
                    CropEffect.ANCHOR, "right-bottom"));
            ToolkitImage center = effect.apply(TOOLKIT, halves(), Map.of("width", 10, "height", 10));

            assertThat(pixel(left, 5, 5)).isEqualTo(Color.RED.getRGB());
            assertThat(pixel(right, 5, 5)).isEqualTo(Color.BLUE.getRGB());
            assertThat(List.of(pixel(center, 2, 2), pixel(center, 7, 7)))
                    .containsExactly(Color.RED.getRGB(), Color.BLUE.getRGB());
        }

        @Test
        void aSideTheImageIsShorterThanComesOutTransparent() {
            ToolkitImage cropped = effect.apply(TOOLKIT, image(10, 10, Color.RED), Map.of("width", 20, "height", 10,
                    CropEffect.ANCHOR, "left-center"));

            assertThat(size(cropped)).containsExactly(20, 10);
            assertThat(pixel(cropped, 15, 5) >>> 24).isZero();
        }

        @Test
        void itOffersNineAnchorsAndFallsBackToTheCenter() {
            List<FormElement> form = effect.settingsForm(Map.of(CropEffect.ANCHOR, "sideways"));

            assertThat(form.get(2).options()).hasSize(9);
            assertThat(form.get(2).value()).isEqualTo(CropEffect.DEFAULT_ANCHOR);
            assertThat(effect.settingsValues(Map.of(name("width"), "10", name("height"), "10",
                    name(CropEffect.ANCHOR), "right-top"))).containsEntry(CropEffect.ANCHOR, "right-top");
            assertThat(effect.settingsValues(Map.of())).containsEntry(CropEffect.ANCHOR, CropEffect.DEFAULT_ANCHOR);
            assertThat(effect.summary(Map.of("width", 10, "height", 20, CropEffect.ANCHOR, "left-top")))
                    .isEqualTo("10 by 20 from the left-top");
            assertThat(effect.transformedSize(new ImageSize(40, 40), Map.of("width", 10, "height", 20)))
                    .contains(new ImageSize(10, 20));
            assertThat(List.of(effect.id(), effect.label())).containsExactly(CropEffect.ID, "Crop");
        }
    }

    @Nested
    class Desaturate {

        private final DesaturateEffect effect = new DesaturateEffect();

        @Test
        void colorsTurnToGrayAndTheSizeStays() {
            ToolkitImage gray = effect.apply(TOOLKIT, image(4, 4, new Color(200, 100, 50)), Map.of());
            Color result = new Color(pixel(gray, 1, 1));

            assertThat(List.of(result.getRed(), result.getGreen(), result.getBlue())).containsOnly(124);
            assertThat(effect.transformedSize(new ImageSize(4, 3), Map.of())).contains(new ImageSize(4, 3));
            assertThat(List.of(effect.id(), effect.label(), effect.summary(Map.of())))
                    .containsExactly(DesaturateEffect.ID, "Desaturate", "");
            assertThat(effect.settingsForm(Map.of())).isEmpty();
            assertThat(effect.settingsValues(Map.of())).isEmpty();
            assertThat(effect.validateSettings(Map.of())).isEmpty();
        }
    }

    @Nested
    class Rotate {

        private final RotateEffect effect = new RotateEffect();

        @Test
        void aRightAngleTurnSwapsTheSides() {
            assertThat(size(effect.apply(TOOLKIT, image(40, 20, Color.RED), Map.of(RotateEffect.DEGREES, 90))))
                    .containsExactly(20, 40);
            assertThat(effect.transformedSize(new ImageSize(40, 20), Map.of(RotateEffect.DEGREES, -90)))
                    .contains(new ImageSize(20, 40));
        }

        @Test
        void anotherTurnGrowsTheImageAndFillsTheCornersWithTheBackground() {
            ToolkitImage filled = effect.apply(TOOLKIT, image(20, 20, Color.RED),
                    Map.of(RotateEffect.DEGREES, 45, RotateEffect.BACKGROUND, "#00ff00"));
            ToolkitImage clear = effect.apply(TOOLKIT, image(20, 20, Color.RED), Map.of(RotateEffect.DEGREES, 45));

            assertThat(size(filled)).containsExactly(28, 28);
            assertThat(pixel(filled, 0, 0)).isEqualTo(Color.GREEN.getRGB());
            assertThat(pixel(clear, 0, 0) >>> 24).isZero();
            assertThat(effect.transformedSize(new ImageSize(20, 20), Map.of(RotateEffect.DEGREES, 45)))
                    .contains(new ImageSize(28, 28));
        }

        @Test
        void settingsThatDoNotParseTurnNothingOnNoBackground() {
            assertThat(effect.settingsValues(Map.of(name(RotateEffect.DEGREES), "quarter",
                    name(RotateEffect.BACKGROUND), "green")))
                    .isEqualTo(Map.of(RotateEffect.DEGREES, 0, RotateEffect.BACKGROUND, ""));
            assertThat(effect.settingsValues(Map.of(name(RotateEffect.DEGREES), "-30",
                    name(RotateEffect.BACKGROUND), "#FFFFFF")))
                    .isEqualTo(Map.of(RotateEffect.DEGREES, -30, RotateEffect.BACKGROUND, "#FFFFFF"));
            assertThat(effect.settingsValues(Map.of()))
                    .isEqualTo(Map.of(RotateEffect.DEGREES, 0, RotateEffect.BACKGROUND, ""));
        }

        @Test
        void itIsDescribedByItsTurnAndBackground() {
            assertThat(effect.summary(Map.of(RotateEffect.DEGREES, 90))).isEqualTo("90 degrees");
            assertThat(effect.summary(Map.of(RotateEffect.DEGREES, 45, RotateEffect.BACKGROUND, "#ffffff")))
                    .isEqualTo("45 degrees on #ffffff");
            assertThat(effect.settingsForm(Map.of(RotateEffect.DEGREES, 45))).extracting(FormElement::value)
                    .containsExactly("45", "");
            assertThat(List.of(effect.id(), effect.label())).containsExactly(RotateEffect.ID, "Rotate");
        }
    }
}
