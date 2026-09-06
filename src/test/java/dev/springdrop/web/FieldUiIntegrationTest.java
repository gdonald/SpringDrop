package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.FieldTableStorage;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.types.BasicStringFormatter;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.types.StringLongFieldType;
import dev.springdrop.kernel.field.widget.types.TextareaWidget;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.schema.SchemaManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class FieldUiIntegrationTest extends AbstractIntegrationTest {

    record Page(long id, String label) {
    }

    record PageType(String id, String label) {
    }

    static final EntityType PAGE = EntityType.content("page", Page.class)
            .withBundles("type", "page_type");

    static final EntityType PAGE_TYPE = EntityType.config("page_type", PageType.class);

    private static final String BUNDLE = "basic";

    private static final String FIELDS_PATH = FieldUiController.PATH_PREFIX + "/page/basic/fields";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FormDisplayManager formDisplays;

    @Autowired
    private ViewDisplayManager viewDisplays;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @BeforeEach
    void aBundleWithNoFieldsYet() {
        entityTypeManager.installStorage("page");
        bundleManager.save("page", new BundleDefinition(BUNDLE, "Basic page"));
    }

    @AfterEach
    void removeDisplaysFieldsAndBundle() {
        formDisplays.delete("page", BUNDLE, FormDisplayConfig.DEFAULT_MODE);
        viewDisplays.delete("page", BUNDLE, ViewDisplayConfig.DEFAULT_MODE);
        fields.fieldNames("page", BUNDLE).forEach(field -> fields.deleteStorage("page", field));
        bundleManager.delete("page", BUNDLE);
    }

    private RequestPostProcessor fieldAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_FIELDS));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(fieldAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void attach(String field, String fieldTypeId, String label) {
        fields.createStorage(FieldStorageConfig.single(field, "page", fieldTypeId));
        fields.createInstance(FieldInstanceConfig.of(field, "page", BUNDLE, label));
    }

    @Test
    void theFieldsTabIsClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(FIELDS_PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theFieldsTabListsEachFieldWithEditAndDeleteButtons() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        Document document = page(FIELDS_PATH);

        assertThat(document.select("tbody tr")).hasSize(1);
        assertThat(document.select("tbody td").getFirst().text()).isEqualTo("Summary");
        BootstrapAssertions.assertEditControlsAreButtons(document);
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void anUnlimitedFieldIsListedAsHoldingAsManyValuesAsAreGiven() throws Exception {
        fields.createStorage(FieldStorageConfig.multiple(
                "tags", "page", StringFieldType.ID, FieldStorageConfig.UNLIMITED));
        fields.createInstance(FieldInstanceConfig.of("tags", "page", BUNDLE, "Tags"));

        assertThat(page(FIELDS_PATH).select("tbody td").get(3).text()).isEqualTo("Unlimited");
    }

    @Test
    void aFieldAddedWithNoCardinalityGivenHoldsOneValue() throws Exception {
        mockMvc.perform(post(FIELDS_PATH + "/add")
                        .param(FieldUiController.STEP, "2")
                        .param(FieldUiController.LABEL, "Byline")
                        .param(FieldUiController.FIELD_NAME, "byline")
                        .param(FieldUiController.FIELD_TYPE, StringFieldType.ID)
                        .param(FieldUiController.CARDINALITY, "")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(fields.findStorage("page", "byline"))
                .hasValueSatisfying(storage -> assertThat(storage.cardinality()).isEqualTo(1));
    }

    @Test
    void aFieldSavedWithNoCardinalityFieldAtAllHoldsOneValue() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        mockMvc.perform(post(FIELDS_PATH + "/summary")
                        .param(FieldUiController.LABEL, "Summary")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(fields.findStorage("page", "summary"))
                .hasValueSatisfying(storage -> assertThat(storage.cardinality()).isEqualTo(1));
    }

    @Test
    void aFieldSavedWithNoWeightFieldAtAllSitsAtTheFront() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        mockMvc.perform(post(DisplayUiController.formDisplayPath("page", BUNDLE))
                        .param("summary_shown", "on")
                        .param("summary_widget", TextareaWidget.ID)
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(formDisplays.find("page", BUNDLE, FormDisplayConfig.DEFAULT_MODE))
                .hasValueSatisfying(display ->
                        assertThat(display.slots().get("summary").weight()).isZero());
    }

    @Test
    void aDisplayTabShowsWhichFieldsAreOffAndWhichLabelsAreHidden() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");
        viewDisplays.save(ViewDisplayConfig.of("page", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .withoutField("summary")
                .withoutLabel("summary"));

        Document document = page(DisplayUiController.viewDisplayPath("page", BUNDLE));

        assertThat(document.selectFirst("input[name=summary_shown]").hasAttr("checked")).isFalse();
        assertThat(document.selectFirst("input[name=summary_label]").hasAttr("checked")).isFalse();
    }

    @Test
    void aFieldLeftOutOfTheDisplayTabSubmissionIsTakenOffTheDisplay() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        mockMvc.perform(post(DisplayUiController.viewDisplayPath("page", BUNDLE))
                        .param("summary_label", "on")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(viewDisplays.find("page", BUNDLE, ViewDisplayConfig.DEFAULT_MODE))
                .hasValueSatisfying(display -> {
                    assertThat(display.disabled()).containsExactly("summary");
                    assertThat(display.showsLabelOf("summary")).isTrue();
                });
    }

    @Test
    void aFieldSavedWithNoWeightGivenSitsAtTheFront() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        mockMvc.perform(post(DisplayUiController.formDisplayPath("page", BUNDLE))
                        .param("summary_shown", "on")
                        .param("summary_widget", TextareaWidget.ID)
                        .param("summary_weight", "")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(formDisplays.find("page", BUNDLE, FormDisplayConfig.DEFAULT_MODE))
                .hasValueSatisfying(display ->
                        assertThat(display.slots().get("summary").weight()).isZero());
    }

    @Test
    void theFieldsTabOffersEveryFieldTypeToAdd() throws Exception {
        Document document = page(FIELDS_PATH);

        assertThat(document.select("select[name=field_type] option"))
                .extracting(option -> option.attr("value"))
                .contains(StringFieldType.ID, StringLongFieldType.ID);
    }

    @Test
    void choosingATypeLeadsToTheSettingsForThatType() throws Exception {
        Document document = Jsoup.parse(mockMvc.perform(post(FIELDS_PATH + "/add")
                        .param(FieldUiController.LABEL, "Summary")
                        .param(FieldUiController.FIELD_NAME, "summary")
                        .param(FieldUiController.FIELD_TYPE, StringFieldType.ID)
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.selectFirst("input[name=cardinality]")).isNotNull();
        assertThat(document.selectFirst("input[name=required]")).isNotNull();
        assertThat(document.selectFirst("input[name=field_name]").attr("value")).isEqualTo("summary");
    }

    @Test
    void savingTheSettingsCreatesTheStorageTheInstanceAndTheTable() throws Exception {
        addSummaryField();

        assertThat(fields.findStorage("page", "summary")).hasValueSatisfying(storage -> {
            assertThat(storage.type()).isEqualTo(StringFieldType.ID);
            assertThat(storage.cardinality()).isEqualTo(3);
        });
        assertThat(fields.findInstance("page", BUNDLE, "summary")).hasValueSatisfying(instance -> {
            assertThat(instance.label()).isEqualTo("Summary");
            assertThat(instance.required()).isTrue();
            assertThat(instance.defaultValue()).isEqualTo("In short");
            assertThat(instance.description()).isEqualTo("One line about the page");
        });
        assertThat(schemaManager.tableExists(FieldTableStorage.tableName(PAGE, "summary"))).isTrue();
    }

    @Test
    void whatWasSetOnAddComesBackOnTheEditForm() throws Exception {
        addSummaryField();

        Document document = page(FIELDS_PATH + "/summary");

        assertThat(document.selectFirst("input[name=cardinality]").attr("value")).isEqualTo("3");
        assertThat(document.selectFirst("input[name=default_value]").attr("value")).isEqualTo("In short");
        assertThat(document.selectFirst("input[name=required]").hasAttr("checked")).isTrue();
    }

    @Test
    void editingAFieldSavesWhatWasChanged() throws Exception {
        addSummaryField();

        mockMvc.perform(post(FIELDS_PATH + "/summary")
                        .param(FieldUiController.LABEL, "Short summary")
                        .param(FieldUiController.CARDINALITY, "1")
                        .param(FieldUiController.DESCRIPTION, "Kept brief")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(fields.findInstance("page", BUNDLE, "summary")).hasValueSatisfying(instance -> {
            assertThat(instance.label()).isEqualTo("Short summary");
            assertThat(instance.required()).isFalse();
        });
        assertThat(fields.findStorage("page", "summary"))
                .hasValueSatisfying(storage -> assertThat(storage.cardinality()).isEqualTo(1));
    }

    @Test
    void deletingAFieldAsksFirstAndThenRemovesEverythingItHad() throws Exception {
        addSummaryField();

        Document confirm = page(FIELDS_PATH + "/summary/delete");
        assertThat(confirm.selectFirst("h1").text()).contains("Delete the field Summary?");
        assertThat(fields.findStorage("page", "summary")).isPresent();

        mockMvc.perform(post(FIELDS_PATH + "/summary/delete").with(fieldAdministrator()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(fields.findStorage("page", "summary")).isEmpty();
        assertThat(fields.findInstance("page", BUNDLE, "summary")).isEmpty();
        assertThat(schemaManager.tableExists(FieldTableStorage.tableName(PAGE, "summary"))).isFalse();
    }

    @Test
    void theFormDisplayTabOffersAWidgetAndAWeightForEachField() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        Document document = page(DisplayUiController.formDisplayPath("page", BUNDLE));

        assertThat(document.selectFirst("select[name=summary_widget]")).isNotNull();
        assertThat(document.selectFirst("input[name=summary_weight]")).isNotNull();
        assertThat(document.selectFirst("input[name=summary_shown]").hasAttr("checked")).isTrue();
    }

    @Test
    void movingAFieldToDisabledTakesItOffTheEditForm() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");
        attach("body", StringLongFieldType.ID, "Body");

        mockMvc.perform(post(DisplayUiController.formDisplayPath("page", BUNDLE))
                        .param("summary_shown", "on")
                        .param("summary_widget", TextareaWidget.ID)
                        .param("summary_weight", "0")
                        .param("body_widget", TextareaWidget.ID)
                        .param("body_weight", "10")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Document editForm = Jsoup.parseBodyFragment(renderer.render(formDisplays.buildContainer(
                "page", BUNDLE, FormDisplayConfig.DEFAULT_MODE, Map.of())));

        assertThat(editForm.select("textarea, input")).extracting(control -> control.attr("name"))
                .containsExactly("summary");
    }

    @Test
    void reorderingOnTheFormDisplayTabReordersTheEditForm() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");
        attach("body", StringLongFieldType.ID, "Body");

        mockMvc.perform(post(DisplayUiController.formDisplayPath("page", BUNDLE))
                        .param("summary_shown", "on").param("summary_widget", "string_textfield")
                        .param("summary_weight", "10")
                        .param("body_shown", "on").param("body_widget", TextareaWidget.ID)
                        .param("body_weight", "0")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Document editForm = Jsoup.parseBodyFragment(renderer.render(formDisplays.buildContainer(
                "page", BUNDLE, FormDisplayConfig.DEFAULT_MODE, Map.of())));

        assertThat(editForm.select("textarea, input")).extracting(control -> control.attr("name"))
                .containsExactly("body", "summary");
    }

    @Test
    void theDisplayTabOffersAFormatterAndALabelToggleForEachField() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        Document document = page(DisplayUiController.viewDisplayPath("page", BUNDLE));

        assertThat(document.selectFirst("select[name=summary_formatter]")).isNotNull();
        assertThat(document.selectFirst("input[name=summary_label]").hasAttr("checked")).isTrue();
    }

    @Test
    void changingAFormatterAndLabelInOneViewModeLeavesAnotherAlone() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");

        mockMvc.perform(post(DisplayUiController.viewDisplayPath("page", BUNDLE))
                        .param(DisplayUiController.MODE, ViewDisplayConfig.TEASER_MODE)
                        .param("summary_shown", "on")
                        .param("summary_formatter", BasicStringFormatter.ID)
                        .param("summary_weight", "0")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Document teaser = Jsoup.parseBodyFragment(viewDisplays.render(
                "page", BUNDLE, ViewDisplayConfig.TEASER_MODE, Map.of("summary", "First\nSecond")));
        Document standard = Jsoup.parseBodyFragment(viewDisplays.render(
                "page", BUNDLE, ViewDisplayConfig.DEFAULT_MODE, Map.of("summary", "First\nSecond")));

        assertThat(teaser.select("br")).hasSize(1);
        assertThat(teaser.select(".field-label")).isEmpty();
        assertThat(standard.selectFirst(".field-label").text()).isEqualTo("Summary");

        viewDisplays.delete("page", BUNDLE, ViewDisplayConfig.TEASER_MODE);
    }

    @Test
    void aDisplayTabRemembersWhatWasChosenTheNextTimeItIsOpened() throws Exception {
        attach("summary", StringFieldType.ID, "Summary");
        mockMvc.perform(post(DisplayUiController.formDisplayPath("page", BUNDLE))
                        .param("summary_shown", "on")
                        .param("summary_widget", TextareaWidget.ID)
                        .param("summary_weight", "7")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Document document = page(DisplayUiController.formDisplayPath("page", BUNDLE));

        assertThat(document.selectFirst("select[name=summary_widget] option[selected]").attr("value"))
                .isEqualTo(TextareaWidget.ID);
        assertThat(document.selectFirst("input[name=summary_weight]").attr("value")).isEqualTo("7");
    }

    private void addSummaryField() throws Exception {
        mockMvc.perform(post(FIELDS_PATH + "/add")
                        .param(FieldUiController.STEP, "2")
                        .param(FieldUiController.LABEL, "Summary")
                        .param(FieldUiController.FIELD_NAME, "summary")
                        .param(FieldUiController.FIELD_TYPE, StringFieldType.ID)
                        .param(FieldUiController.CARDINALITY, "3")
                        .param(FieldUiController.REQUIRED, "on")
                        .param(FieldUiController.DESCRIPTION, "One line about the page")
                        .param(FieldUiController.DEFAULT_VALUE, "In short")
                        .with(fieldAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @TestConfiguration
    static class PageTypes {

        @Bean
        EntityTypeProvider pageTypes() {
            return () -> List.of(PAGE, PAGE_TYPE);
        }
    }
}
