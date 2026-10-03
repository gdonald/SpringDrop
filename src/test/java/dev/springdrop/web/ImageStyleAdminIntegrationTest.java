package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.image.EffectConfig;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.image.effects.CropEffect;
import dev.springdrop.kernel.image.effects.DesaturateEffect;
import dev.springdrop.kernel.image.effects.ScaleEffect;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ImageStyleAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String STYLE = "card";

    private static final String MANAGE = ImageStyleController.managePath(STYLE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImageStyleManager styles;

    @BeforeEach
    void aCardStyleThatScales() {
        styles.save(new ImageStyle(STYLE, "Card", List.of(
                new EffectConfig("scale", ScaleEffect.ID, 0, Map.of("width", 300)),
                new EffectConfig("gray", DesaturateEffect.ID, 1, Map.of()))));
    }

    @AfterEach
    void removeStyles() {
        styles.all().stream().map(ImageStyle::id)
                .filter(id -> !List.of("thumbnail", "medium", "large", "wide", "square").contains(id))
                .forEach(styles::delete);
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(ImageStyleManager.ADMINISTER_IMAGE_STYLES));
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(administrator())).andReturn().getResponse();
    }

    private MockHttpServletResponse submit(String path, Map<String, String> fields) throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(csrf());
        fields.forEach(request::param);
        return perform(request);
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(perform(get(path)).getContentAsString());
    }

    private ImageStyle card() {
        return styles.find(STYLE).orElseThrow();
    }

    private static String setting(String name) {
        return ImageEffect.SETTINGS_PREFIX + name;
    }

    @Test
    void theListShowsEachStyleWithItsEffects() throws Exception {
        Document list = page(ImageStyleController.PATH);

        assertThat(list.selectFirst("tr[data-style=card]").text()).contains("Card")
                .contains("Scale 300 by any height, Desaturate");
        assertThat(list.selectFirst("tr[data-style=card] a.btn-secondary").attr("href")).isEqualTo(MANAGE);
    }

    @Test
    void someoneWithoutThePermissionCannotManageStyles() throws Exception {
        assertThat(mockMvc.perform(get(ImageStyleController.PATH).with(user("visitor"))).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get(MANAGE).with(user("visitor"))).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void addingAStyleNamesItAndOpensItForEffects() throws Exception {
        assertThat(page(ImageStyleController.PATH + "/add").select("input[name=label]")).hasSize(1);

        MockHttpServletResponse response = submit(ImageStyleController.PATH + "/add", Map.of("label", "Hero banner"));

        assertThat(response.getRedirectedUrl()).isEqualTo(ImageStyleController.managePath("hero_banner"));
        assertThat(styles.find("hero_banner")).map(ImageStyle::effects).contains(List.of());
    }

    @Test
    void aStyleWithoutANameIsNotAdded() throws Exception {
        MockHttpServletResponse response = submit(ImageStyleController.PATH + "/add", Map.of("label", " "));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(Jsoup.parse(response.getContentAsString()).text()).contains("This value is required.");
    }

    @Test
    void theEditPageListsTheEffectsWithTheirWeightsAndActions() throws Exception {
        Document edit = page(MANAGE);

        assertThat(edit.text()).contains("Scale 300 by any height").contains("Desaturate");
        assertThat(edit.selectFirst("input[name=weight_scale]").val()).isEqualTo("0");
        assertThat(edit.selectFirst("a[href=" + MANAGE + "/effects/scale]").text()).isEqualTo("Edit");
        assertThat(edit.select("a[href=" + MANAGE + "/effects/gray]")).isEmpty();
        assertThat(edit.selectFirst("a[href=" + MANAGE + "/effects/gray/delete]").text()).isEqualTo("Remove");
        assertThat(edit.select("select[name=new_effect] option")).hasSize(6);
    }

    @Test
    void savingTheStyleKeepsItsNameAndReordersItsEffects() throws Exception {
        MockHttpServletResponse response = submit(MANAGE, Map.of("label", "Card image", "weight_scale", "5",
                "weight_gray", "-1"));

        assertThat(response.getRedirectedUrl()).isEqualTo(ImageStyleController.PATH);
        assertThat(card().label()).isEqualTo("Card image");
        assertThat(card().effects()).extracting(EffectConfig::id).containsExactly("gray", "scale");
    }

    @Test
    void aWeightThatIsNotAWholeNumberIsRefused() throws Exception {
        MockHttpServletResponse response = submit(MANAGE, Map.of("label", "Card", "weight_scale", "first"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(Jsoup.parse(response.getContentAsString()).text()).contains("The weight is a whole number.");
        assertThat(card().effects()).extracting(EffectConfig::id).containsExactly("scale", "gray");
    }

    @Test
    void addingAnEffectWithSettingsGoesOnToItsForm() throws Exception {
        MockHttpServletResponse response = submit(MANAGE, Map.of("label", "Card", "new_effect", CropEffect.ID,
                ImageStyleController.ADD_EFFECT, ""));

        assertThat(response.getRedirectedUrl()).isEqualTo(MANAGE + "/add/" + CropEffect.ID);
        assertThat(page(MANAGE + "/add/" + CropEffect.ID).select("select[name=" + setting(CropEffect.ANCHOR) + "]"))
                .hasSize(1);
    }

    @Test
    void addingAnEffectWithoutSettingsPutsItStraightIntoTheStyle() throws Exception {
        MockHttpServletResponse response = submit(MANAGE, Map.of("label", "Card", "new_effect", DesaturateEffect.ID,
                ImageStyleController.ADD_EFFECT, ""));

        assertThat(response.getRedirectedUrl()).isEqualTo(MANAGE);
        assertThat(card().effects()).extracting(EffectConfig::id).containsExactly("scale", "gray", "image_desaturate");
        assertThat(card().effects().get(2).weight()).isEqualTo(2);
    }

    @Test
    void anEffectTheSiteDoesNotHaveCannotBeAdded() throws Exception {
        MockHttpServletResponse response = submit(MANAGE, Map.of("label", "Card", "new_effect", "image_sparkle",
                ImageStyleController.ADD_EFFECT, ""));

        assertThat(Jsoup.parse(response.getContentAsString()).text()).contains("Choose an effect to add.");
        assertThat(perform(get(MANAGE + "/add/image_sparkle")).getStatus()).isEqualTo(404);
    }

    @Test
    void anEffectIsAddedWithTheSettingsSubmitted() throws Exception {
        MockHttpServletResponse response = submit(MANAGE + "/add/" + CropEffect.ID, Map.of(
                setting("width"), "120", setting("height"), "80", setting(CropEffect.ANCHOR), "left-top"));

        assertThat(response.getRedirectedUrl()).isEqualTo(MANAGE);
        assertThat(card().effect(CropEffect.ID)).map(EffectConfig::settings)
                .contains(Map.of("width", 120, "height", 80, CropEffect.ANCHOR, "left-top"));
    }

    @Test
    void anEffectWithSettingsThatBreakTheRulesIsNotAdded() throws Exception {
        MockHttpServletResponse response = submit(MANAGE + "/add/" + CropEffect.ID, Map.of(
                setting("width"), "0", setting("height"), ""));

        Document form = Jsoup.parse(response.getContentAsString());
        assertThat(form.text()).contains("Use a whole number of pixels from 1 to 99999.")
                .contains("This value is required.");
        assertThat(card().effect(CropEffect.ID)).isEmpty();
    }

    @Test
    void aScaleNeedsAtLeastOneSide() throws Exception {
        MockHttpServletResponse response = submit(MANAGE + "/add/" + ScaleEffect.ID, Map.of());

        assertThat(Jsoup.parse(response.getContentAsString()).select(".invalid-feedback")).hasSize(2);
    }

    @Test
    void anEffectsSettingsAreEditedInPlace() throws Exception {
        assertThat(page(MANAGE + "/effects/scale").selectFirst("input[name=" + setting("width") + "]").val())
                .isEqualTo("300");

        MockHttpServletResponse response = submit(MANAGE + "/effects/scale", Map.of(setting("width"), "150"));

        assertThat(response.getRedirectedUrl()).isEqualTo(MANAGE);
        assertThat(card().effect("scale")).map(EffectConfig::settings)
                .contains(Map.of("width", 150, ScaleEffect.UPSCALE, false));
        assertThat(card().effect("scale")).map(EffectConfig::weight).contains(0);
    }

    @Test
    void anEffectIsRemovedThroughItsConfirmForm() throws Exception {
        assertThat(page(MANAGE + "/effects/gray/delete").text()).contains("Remove the Desaturate effect from Card?");

        submit(MANAGE + "/effects/gray/delete", Map.of());

        assertThat(card().effects()).extracting(EffectConfig::id).containsExactly("scale");
    }

    @Test
    void anEffectWhosePluginIsGoneIsMarkedMissingAndCanBeRemoved() throws Exception {
        styles.save(card().withEffect(new EffectConfig("old", "retired_effect", 3, Map.of())));

        assertThat(page(MANAGE).text()).contains("Missing effect retired_effect");
        assertThat(page(ImageStyleController.PATH).selectFirst("tr[data-style=card]").text())
                .doesNotContain("retired_effect");
        assertThat(page(MANAGE + "/effects/old/delete").text()).contains("Remove the retired_effect effect");
        assertThat(perform(get(MANAGE + "/effects/old")).getStatus()).isEqualTo(404);
    }

    @Test
    void aStyleWithoutEffectsSaysSo() throws Exception {
        styles.save(new ImageStyle(STYLE, "Card", List.of()));

        assertThat(page(MANAGE).text()).contains("There are no effects yet.");
        assertThat(page(ImageStyleController.PATH).selectFirst("tr[data-style=card]").text()).contains("No effects");
    }

    @Test
    void flushingAsksFirst() throws Exception {
        assertThat(page(MANAGE + "/flush").text()).contains("Flush the images drawn with Card?");

        assertThat(submit(MANAGE + "/flush", Map.of()).getRedirectedUrl()).isEqualTo(MANAGE);
    }

    @Test
    void aStyleIsDeletedThroughItsConfirmForm() throws Exception {
        assertThat(page(MANAGE + "/delete").text()).contains("Delete the image style Card?");

        assertThat(submit(MANAGE + "/delete", Map.of()).getRedirectedUrl()).isEqualTo(ImageStyleController.PATH);
        assertThat(styles.find(STYLE)).isEmpty();
    }

    @Test
    void aStyleOrEffectTheSiteDoesNotHaveIsNotFound() throws Exception {
        assertThat(perform(get(ImageStyleController.managePath("retired"))).getStatus()).isEqualTo(404);
        assertThat(perform(get(MANAGE + "/effects/nothing")).getStatus()).isEqualTo(404);
    }
}
