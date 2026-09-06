package dev.springdrop.kernel.field.display;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.types.StringLongFieldType;
import dev.springdrop.kernel.field.widget.types.TextareaWidget;
import dev.springdrop.kernel.field.widget.types.TextfieldWidget;
import dev.springdrop.kernel.form.FormRenderer;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class FormDisplayIntegrationTest extends AbstractIntegrationTest {

    record Recipe(long id, String label) {
    }

    record RecipeType(String id, String label) {
    }

    static final EntityType DISH = EntityType.content("dish", Recipe.class)
            .withBundles("type", "dish_type");

    static final EntityType DISH_TYPE = EntityType.config("dish_type", RecipeType.class);

    private static final String BUNDLE = "supper";

    private static final String EDIT_MODE = "edit";

    @Autowired
    private FormDisplayManager displays;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @BeforeEach
    void aBundleWithTwoFields() {
        entityTypeManager.installStorage("dish");
        bundleManager.save("dish", new BundleDefinition(BUNDLE, "Supper"));
        attach("summary", StringFieldType.ID);
        attach("method", StringLongFieldType.ID);
    }

    @AfterEach
    void removeDisplayFieldsAndBundle() {
        displays.delete("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE);
        displays.delete("dish", BUNDLE, EDIT_MODE);
        fields.fieldNames("dish", BUNDLE).forEach(field -> fields.deleteStorage("dish", field));
        bundleManager.delete("dish", BUNDLE);
    }

    private void attach(String field, String fieldTypeId) {
        fields.createStorage(FieldStorageConfig.single(field, "dish", fieldTypeId));
        fields.createInstance(FieldInstanceConfig.of(field, "dish", BUNDLE, field));
    }

    private Document render(String mode, Map<String, Object> values) {
        return Jsoup.parseBodyFragment(renderer.render(
                displays.buildContainer("dish", BUNDLE, mode, values)));
    }

    private List<String> fieldOrder(Document document) {
        return document.select("input, textarea").stream().map(control -> control.attr("name")).toList();
    }

    @Test
    void aBundleWithNoDisplaySavedShowsEveryFieldItHas() {
        Document document = render(FormDisplayConfig.DEFAULT_MODE, Map.of());

        assertThat(fieldOrder(document)).containsExactlyInAnyOrder("summary", "method");
    }

    @Test
    void reorderingTheDisplayReordersTheForm() {
        displays.save(FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("method", TextareaWidget.ID, 0))
                .with(FieldDisplaySlot.of("summary", TextfieldWidget.ID, 10)));

        assertThat(fieldOrder(render(FormDisplayConfig.DEFAULT_MODE, Map.of())))
                .containsExactly("method", "summary");

        displays.save(FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("summary", TextfieldWidget.ID, 0))
                .with(FieldDisplaySlot.of("method", TextareaWidget.ID, 10)));

        assertThat(fieldOrder(render(FormDisplayConfig.DEFAULT_MODE, Map.of())))
                .containsExactly("summary", "method");
    }

    @Test
    void changingTheWidgetInTheDisplayChangesTheControl() {
        displays.save(FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("summary", TextareaWidget.ID, 0)));

        Document document = render(FormDisplayConfig.DEFAULT_MODE, Map.of());

        assertThat(document.selectFirst("textarea[name=summary]")).isNotNull();
    }

    @Test
    void aFieldMovedToTheDisabledRegionLeavesTheForm() {
        displays.save(FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("summary", TextfieldWidget.ID, 0))
                .withoutField("method"));

        assertThat(fieldOrder(render(FormDisplayConfig.DEFAULT_MODE, Map.of()))).containsExactly("summary");
    }

    @Test
    void aFieldPlacedAgainComesBackOutOfTheDisabledRegion() {
        FormDisplayConfig display = FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .withoutField("method")
                .with(FieldDisplaySlot.of("method", TextareaWidget.ID, 0));

        assertThat(display.disabled()).isEmpty();
        assertThat(display.slots()).containsKey("method");
    }

    @Test
    void placingAFieldLeavesAnotherFieldsDisabledPlacementAlone() {
        FormDisplayConfig display = FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .withoutField("method")
                .with(FieldDisplaySlot.of("summary", TextfieldWidget.ID, 0));

        assertThat(display.disabled()).containsExactly("method");
        assertThat(display.slots()).containsKey("summary");
    }

    @Test
    void disablingAFieldTwiceLeavesItDisabledOnce() {
        FormDisplayConfig display = FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .withoutField("method")
                .withoutField("method");

        assertThat(display.disabled()).containsExactly("method");
    }

    @Test
    void eachFormModeIsLaidOutOnItsOwn() {
        displays.save(FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("summary", TextfieldWidget.ID, 0))
                .withoutField("method"));
        displays.save(FormDisplayConfig.of("dish", BUNDLE, EDIT_MODE)
                .with(FieldDisplaySlot.of("method", TextareaWidget.ID, 0))
                .withoutField("summary"));

        assertThat(fieldOrder(render(FormDisplayConfig.DEFAULT_MODE, Map.of()))).containsExactly("summary");
        assertThat(fieldOrder(render(EDIT_MODE, Map.of()))).containsExactly("method");
    }

    @Test
    void aDisplayCarriesTheSettingsItsWidgetRunsWith() {
        displays.save(FormDisplayConfig.of("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                .with(new FieldDisplaySlot("summary", TextfieldWidget.ID, 0,
                        Map.of("placeholder", "In one line"))));

        assertThat(render(FormDisplayConfig.DEFAULT_MODE, Map.of())
                .selectFirst("input[name=summary]")).isNotNull();
        assertThat(displays.find("dish", BUNDLE, FormDisplayConfig.DEFAULT_MODE))
                .hasValueSatisfying(display -> assertThat(display.slots().get("summary").settings())
                        .containsEntry("placeholder", "In one line"));
    }

    @Test
    void theFormIsFilledInWithTheValuesItIsGiven() {
        Document document = render(FormDisplayConfig.DEFAULT_MODE,
                Map.of("summary", "A quick supper", "method", List.of("Chop", "Fry")));

        assertThat(document.selectFirst("input[name=summary]").attr("value")).isEqualTo("A quick supper");
        assertThat(document.selectFirst("textarea[name=method]").text()).isEqualTo("Chop");
    }

    @Test
    void aFieldWithNoValueRendersEmpty() {
        assertThat(render(FormDisplayConfig.DEFAULT_MODE, Map.of())
                .selectFirst("input[name=summary]").attr("value")).isEmpty();
    }

    @Test
    void aDisplayThatWasDeletedIsGone() {
        displays.save(FormDisplayConfig.of("dish", BUNDLE, EDIT_MODE));

        displays.delete("dish", BUNDLE, EDIT_MODE);

        assertThat(displays.find("dish", BUNDLE, EDIT_MODE)).isEmpty();
    }

    @TestConfiguration
    static class DishTypes {

        @Bean
        EntityTypeProvider dishTypes() {
            return () -> List.of(DISH, DISH_TYPE);
        }
    }
}
