package dev.springdrop.kernel.field.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.field.AllowedValue;
import dev.springdrop.kernel.field.AllowedValues;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.BooleanFieldType;
import dev.springdrop.kernel.field.types.DateTimeFieldType;
import dev.springdrop.kernel.field.types.IntegerFieldType;
import dev.springdrop.kernel.field.types.ListStringFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.types.StringLongFieldType;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import dev.springdrop.kernel.field.widget.types.BooleanCheckboxWidget;
import dev.springdrop.kernel.field.widget.types.DateWidget;
import dev.springdrop.kernel.field.widget.types.NumberWidget;
import dev.springdrop.kernel.field.widget.types.OptionsButtonsWidget;
import dev.springdrop.kernel.field.widget.types.OptionsSelectWidget;
import dev.springdrop.kernel.field.widget.types.TextareaWidget;
import dev.springdrop.kernel.field.widget.types.TextfieldWidget;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import dev.springdrop.web.FieldWidgetController;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FieldWidgetIntegrationTest extends AbstractIntegrationTest {

    record Card(long id, String label) {
    }

    record CardType(String id, String label) {
    }

    static final EntityType CARD = EntityType.content("card", Card.class)
            .withBundles("type", "card_type");

    static final EntityType CARD_TYPE = EntityType.config("card_type", CardType.class);

    private static final String BUNDLE = "note";

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void aBundleToAttachFieldsTo() {
        entityTypeManager.installStorage("card");
        bundleManager.save("card", new BundleDefinition(BUNDLE, "Note"));
    }

    @AfterEach
    void removeFieldsAndBundle() {
        fields.fieldNames("card", BUNDLE).forEach(field -> fields.deleteStorage("card", field));
        bundleManager.delete("card", BUNDLE);
    }

    private WidgetContext attach(FieldStorageConfig storage, FieldInstanceConfig instance) {
        fields.createStorage(storage);
        fields.createInstance(instance);
        return widgets.context("card", BUNDLE, storage.name());
    }

    private WidgetContext attachSingle(String field, String fieldTypeId) {
        return attach(FieldStorageConfig.single(field, "card", fieldTypeId),
                FieldInstanceConfig.of(field, "card", BUNDLE, field));
    }

    private Document render(WidgetContext context, List<Object> values) {
        return Jsoup.parseBodyFragment(renderer.render(widgets.build(context, values, 0)));
    }

    @Test
    void aSingleValueFieldRendersOneControlNamedAfterTheField() {
        WidgetContext context = attachSingle("title", StringFieldType.ID);

        Document document = render(context, List.of("Hello"));

        assertThat(document.select("input[type=text]")).singleElement()
                .satisfies(input -> {
                    assertThat(input.attr("name")).isEqualTo("title");
                    assertThat(input.attr("value")).isEqualTo("Hello");
                });
        assertThat(document.select("button")).isEmpty();
    }

    @Test
    void aMultiValueFieldRendersOneControlPerValueNumberedByDelta() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, FieldStorageConfig.UNLIMITED),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        Document document = render(context, List.of("news", "local"));

        assertThat(document.select("input[type=text]")).extracting(input -> input.attr("name"))
                .containsExactly("tags[0]", "tags[1]");
    }

    @Test
    void anEmptyMultiValueFieldStillOffersOneControl() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, FieldStorageConfig.UNLIMITED),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        assertThat(render(context, List.of()).select("input[type=text]")).hasSize(1);
    }

    @Test
    void onlyTheFirstDeltaCarriesTheLabelAndTheRequiredMark() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, FieldStorageConfig.UNLIMITED),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags").asRequired());

        Document document = render(context, List.of("news", "local"));

        assertThat(document.select("label.form-label")).hasSize(1);
        assertThat(document.select("input[required]")).hasSize(1);
    }

    @Test
    void aFieldThatCanHoldMoreOffersToAddAnother() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, FieldStorageConfig.UNLIMITED),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        Document document = render(context, List.of("news"));

        assertThat(document.selectFirst("button").text()).isEqualTo("Add another item");
        assertThat(document.selectFirst("button").attr("hx-post")).isEqualTo(FieldWidgetPaths.ADD_MORE);
        assertThat(document.selectFirst("button").attr("hx-target")).isEqualTo("#field-tags");
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void aFieldAtItsLimitOffersNoMoreDeltas() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, 2),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        Document document = render(context, List.of("news", "local"));

        assertThat(document.select("input[type=text]")).hasSize(2);
        assertThat(document.select("button")).isEmpty();
    }

    @Test
    void addingAnotherItemReturnsTheFieldOneDeltaLongerWithoutLosingWhatWasEntered() throws Exception {
        attach(FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, FieldStorageConfig.UNLIMITED),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        String fragment = mockMvc.perform(post(FieldWidgetPaths.ADD_MORE)
                        .header("HX-Request", "true")
                        .param(FieldWidgetController.ENTITY_TYPE, "card")
                        .param(FieldWidgetController.BUNDLE, BUNDLE)
                        .param(FieldWidgetController.FIELD, "tags")
                        .param("tags[0]", "news")
                        .param("tags[1]", "local")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment).doesNotContain("<html");
        assertThat(Jsoup.parseBodyFragment(fragment).select("input[type=text]")).extracting(input -> input.attr("value"))
                .containsExactly("news", "local", "");
    }

    @Test
    void theSubmittedValuesOfAFieldAreReadBackInDeltaOrder() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, FieldStorageConfig.UNLIMITED),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        List<Object> values = widgets.extract(context, Map.of("tags[0]", "news", "tags[1]", "local"));

        assertThat(values).containsExactly("news", "local");
    }

    @Test
    void anItemLeftEmptyIsNotStored() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, 3),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        List<Object> values = widgets.extract(context, Map.of("tags[0]", "news", "tags[1]", "   "));

        assertThat(values).containsExactly("news");
    }

    @Test
    void aSingleValueFieldReadsBackTheValueUnderItsOwnName() {
        WidgetContext context = attachSingle("title", StringFieldType.ID);

        assertThat(widgets.extract(context, Map.of("title", "Hello"))).containsExactly("Hello");
    }

    @Test
    void aLongTextFieldGetsATextarea() {
        WidgetContext context = attachSingle("body", StringLongFieldType.ID);

        assertThat(render(context, List.of("Some text")).selectFirst("textarea").text())
                .isEqualTo("Some text");
        assertThat(widgets.widget(context).id()).isEqualTo(TextareaWidget.ID);
    }

    @Test
    void aNumberFieldGetsItsBoundsOnBothSides() {
        WidgetContext context = attach(
                FieldStorageConfig.single("count", "card", IntegerFieldType.ID),
                FieldInstanceConfig.of("count", "card", BUNDLE, "Count")
                        .withSettings(Map.of(FieldSettings.MIN, 1, FieldSettings.MAX, 10, "step", 1)));

        Document document = render(context, List.of(5));

        assertThat(document.selectFirst("input[type=number]").attr("min")).isEqualTo("1");
        assertThat(document.selectFirst("input[type=number]").attr("max")).isEqualTo("10");
        assertThat(document.selectFirst("input[type=number]").attr("step")).isEqualTo("1");
        assertThat(document.selectFirst("input[type=number]").attr("data-rule-range")).isEqualTo("1:10");
    }

    @Test
    void aNumberFieldWithoutBoundsCarriesNoRule() {
        WidgetContext context = attachSingle("count", IntegerFieldType.ID);

        assertThat(render(context, List.of(5)).selectFirst("input").hasAttr("data-rule-range")).isFalse();
    }

    @Test
    void aBooleanFieldGetsACheckboxLabelledWithWhatOnMeans() {
        WidgetContext context = attach(
                FieldStorageConfig.single("published", "card", BooleanFieldType.ID),
                FieldInstanceConfig.of("published", "card", BUNDLE, "Published")
                        .withSettings(Map.of(FieldSettings.ON_LABEL, "Live")));

        Document document = render(context, List.of(true));

        assertThat(document.selectFirst("input[type=checkbox]").hasAttr("checked")).isTrue();
        assertThat(document.selectFirst("label.form-check-label").text()).isEqualTo("Live");
    }

    @Test
    void anUntickedBoxIsReadAsOffRatherThanAsNothingEntered() {
        WidgetContext context = attachSingle("published", BooleanFieldType.ID);

        assertThat(widgets.extract(context, Map.of())).containsExactly(false);
        assertThat(widgets.extract(context, Map.of("published", "on"))).containsExactly(true);
        assertThat(render(context, List.of()).selectFirst("input[type=checkbox]").hasAttr("checked")).isFalse();
    }

    @Test
    void anOptionsFieldGetsASelectOfItsAllowedValues() {
        WidgetContext context = attach(
                new FieldStorageConfig("mood", "card", ListStringFieldType.ID, 1,
                        Map.of(FieldSettings.ALLOWED_VALUES, AllowedValues.setting(
                                List.of(new AllowedValue("happy", "Happy"), new AllowedValue("sad", "Sad"))))),
                FieldInstanceConfig.of("mood", "card", BUNDLE, "Mood"));

        Document document = render(context, List.of("sad"));

        assertThat(document.select("option")).extracting(option -> option.attr("value"))
                .containsExactly("", "happy", "sad");
        assertThat(document.selectFirst("option[selected]").attr("value")).isEqualTo("sad");
    }

    @Test
    void aRequiredOptionsFieldOffersNoEmptyChoice() {
        WidgetContext context = attach(
                new FieldStorageConfig("mood", "card", ListStringFieldType.ID, 1,
                        Map.of(FieldSettings.ALLOWED_VALUES, AllowedValues.setting(
                                List.of(new AllowedValue("happy", "Happy"))))),
                FieldInstanceConfig.of("mood", "card", BUNDLE, "Mood").asRequired());

        assertThat(render(context, List.of()).select("option")).extracting(option -> option.attr("value"))
                .containsExactly("happy");
    }

    @Test
    void aFieldCanNameAnotherWidgetThanItsTypesDefault() {
        WidgetContext context = attach(
                new FieldStorageConfig("mood", "card", ListStringFieldType.ID, 1,
                        Map.of(FieldSettings.ALLOWED_VALUES, AllowedValues.setting(
                                List.of(new AllowedValue("happy", "Happy"), new AllowedValue("sad", "Sad"))))),
                FieldInstanceConfig.of("mood", "card", BUNDLE, "Mood")
                        .withSettings(Map.of(FieldWidgetManager.WIDGET_SETTING, OptionsButtonsWidget.ID)));

        Document document = render(context, List.of("happy"));

        assertThat(widgets.widget(context).id()).isEqualTo(OptionsButtonsWidget.ID);
        assertThat(document.select("input[type=radio]")).hasSize(3);
        assertThat(document.selectFirst("input[checked]").attr("value")).isEqualTo("happy");
    }

    @Test
    void aMultiValueOptionsFieldOffersCheckboxesRatherThanRadios() {
        WidgetContext context = attach(
                new FieldStorageConfig("moods", "card", ListStringFieldType.ID, FieldStorageConfig.UNLIMITED,
                        Map.of(FieldSettings.ALLOWED_VALUES, AllowedValues.setting(
                                List.of(new AllowedValue("happy", "Happy"))))),
                FieldInstanceConfig.of("moods", "card", BUNDLE, "Moods")
                        .withSettings(Map.of(FieldWidgetManager.WIDGET_SETTING, OptionsButtonsWidget.ID)));

        assertThat(render(context, List.of("happy")).select("input[type=checkbox]")).isNotEmpty();
    }

    @Test
    void everyFieldTypeReachesTheWidgetItAsksFor() {
        assertThat(widgets.widget(attachSingle("published", BooleanFieldType.ID)).id())
                .isEqualTo(BooleanCheckboxWidget.ID);
        assertThat(widgets.widget(attachSingle("title", StringFieldType.ID)).id())
                .isEqualTo(TextfieldWidget.ID);
        assertThat(widgets.widget(attachSingle("count", IntegerFieldType.ID)).id())
                .isEqualTo(NumberWidget.ID);
        assertThat(widgets.widget(attachSingle("starts_on", DateTimeFieldType.ID)).id())
                .isEqualTo(DateWidget.ID);
        assertThat(widgets.widget(attachSingle("mood", ListStringFieldType.ID)).id())
                .isEqualTo(OptionsSelectWidget.ID);
    }

    @Test
    void aDateFieldGetsANativeDateInput() {
        WidgetContext context = attach(
                new FieldStorageConfig("starts_on", "card", DateTimeFieldType.ID, 1,
                        Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_ONLY)),
                FieldInstanceConfig.of("starts_on", "card", BUNDLE, "Starts on"));

        assertThat(render(context, List.of("2026-03-05")).selectFirst("input[type=date]").attr("value"))
                .isEqualTo("2026-03-05");
    }

    @Test
    void anOptionsFieldWithNoAllowedValuesOffersNoChoices() {
        WidgetContext context = attachSingle("mood", ListStringFieldType.ID);

        assertThat(render(context, List.of()).select("option")).extracting(option -> option.attr("value"))
                .containsExactly("");
    }

    @Test
    void anUnlimitedBoxIsReadAsOnFromEitherWayABrowserSendsIt() {
        WidgetContext context = attachSingle("published", BooleanFieldType.ID);

        assertThat(widgets.extract(context, Map.of("published", "true"))).containsExactly(true);
        assertThat(widgets.extract(context, Map.of("published", "off"))).containsExactly(false);
    }

    @Test
    void aNumberFieldBoundedOnOneSideOnlyCarriesThatBound() {
        WidgetContext context = attach(
                FieldStorageConfig.single("count", "card", IntegerFieldType.ID),
                FieldInstanceConfig.of("count", "card", BUNDLE, "Count")
                        .withSettings(Map.of(FieldSettings.MAX, 10)));

        Document document = render(context, List.of(5));

        assertThat(document.selectFirst("input").hasAttr("min")).isFalse();
        assertThat(document.selectFirst("input").attr("data-rule-range")).isEqualTo(":10");
    }

    @Test
    void aLimitedFieldThatIsNotFullStillOffersToAddAnother() {
        WidgetContext context = attach(
                FieldStorageConfig.multiple("tags", "card", StringFieldType.ID, 3),
                FieldInstanceConfig.of("tags", "card", BUNDLE, "Tags"));

        Document document = render(context, List.of("news"));

        assertThat(document.select("input[type=text]")).hasSize(1);
        assertThat(document.selectFirst("button").text()).isEqualTo("Add another item");
    }

    @Test
    void aFieldThatIsNotThereHasNoWidgetToBuild() {
        assertThatThrownBy(() -> widgets.context("card", BUNDLE, "nonesuch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonesuch");
    }

    @Test
    void aFieldThatIsNotOnThisBundleHasNoWidgetToBuild() {
        fields.createStorage(FieldStorageConfig.single("elsewhere", "card", StringFieldType.ID));

        assertThatThrownBy(() -> widgets.context("card", "other_bundle", "elsewhere"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("other_bundle");
    }

    @TestConfiguration
    static class CardTypes {

        @Bean
        EntityTypeProvider cardTypes() {
            return () -> List.of(CARD, CARD_TYPE);
        }
    }
}
