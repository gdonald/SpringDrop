package dev.springdrop.kernel.field.display;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.formatter.types.BasicStringFormatter;
import dev.springdrop.kernel.field.formatter.types.EntityReferenceIdFormatter;
import dev.springdrop.kernel.field.formatter.types.EntityReferenceLabelFormatter;
import dev.springdrop.kernel.field.formatter.types.EntityReferenceViewFormatter;
import dev.springdrop.kernel.field.formatter.types.StringFormatter;
import dev.springdrop.kernel.field.formatter.FieldFormatterManager;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
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
class ViewDisplayIntegrationTest extends AbstractIntegrationTest {

    record Story(long id, String label) {
    }

    record StoryType(String id, String label) {
    }

    static final EntityType STORY = EntityType.content("story", Story.class)
            .withBundles("type", "story_type")
            .withLinks(Map.of(EntityReferenceLabelFormatter.CANONICAL, "/story/{story}"));

    static final EntityType STORY_TYPE = EntityType.config("story_type", StoryType.class);

    private static final String BUNDLE = "report";

    @Autowired
    private ViewDisplayManager displays;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @BeforeEach
    void aBundleWithTwoFields() {
        entityTypeManager.installStorage("story");
        bundleManager.save("story", new BundleDefinition(BUNDLE, "Report"));
        attach(FieldStorageConfig.single("summary", "story", StringFieldType.ID), "Summary");
        attach(FieldStorageConfig.single("byline", "story", StringFieldType.ID), "Byline");
    }

    @AfterEach
    void removeDisplaysFieldsAndBundle() {
        List.of(ViewDisplayConfig.DEFAULT_MODE, ViewDisplayConfig.TEASER_MODE, ViewDisplayConfig.FULL_MODE)
                .forEach(mode -> displays.delete("story", BUNDLE, mode));
        fields.fieldNames("story", BUNDLE).forEach(field -> fields.deleteStorage("story", field));
        bundleManager.delete("story", BUNDLE);
        List.of(1L, 2L).forEach(id -> entities.delete("story", id));
    }

    private void attach(FieldStorageConfig storage, String label) {
        fields.createStorage(storage);
        fields.createInstance(FieldInstanceConfig.of(storage.name(), "story", BUNDLE, label));
    }

    private Document render(String mode, Map<String, Object> values) {
        return Jsoup.parseBodyFragment(displays.render("story", BUNDLE, mode, values));
    }

    private static Map<String, Object> report() {
        return Map.of("summary", "A short summary", "byline", "By Alice");
    }

    @Test
    void aBundleWithNoDisplaySavedRendersEveryFieldWithItsLabel() {
        Document document = render(ViewDisplayConfig.DEFAULT_MODE, report());

        assertThat(document.select(".field-label")).extracting(element -> element.text())
                .containsExactlyInAnyOrder("Summary", "Byline");
    }

    @Test
    void theDisplayDecidesTheOrderTheFieldsAreRead() {
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("byline", StringFormatter.ID, 0))
                .with(FieldDisplaySlot.of("summary", StringFormatter.ID, 10)));

        assertThat(render(ViewDisplayConfig.DEFAULT_MODE, report()).select(".field-label"))
                .extracting(element -> element.text())
                .containsExactly("Byline", "Summary");
    }

    @Test
    void aLabelTheDisplayHidesIsNotRendered() {
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("summary", StringFormatter.ID, 0))
                .withoutLabel("summary")
                .withoutField("byline"));

        Document document = render(ViewDisplayConfig.DEFAULT_MODE, report());

        assertThat(document.select(".field-label")).isEmpty();
        assertThat(document.selectFirst(".field-item").text()).isEqualTo("A short summary");
    }

    @Test
    void hidingALabelTwiceHidesItOnce() {
        ViewDisplayConfig display = ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .withoutLabel("summary")
                .withoutLabel("summary");

        assertThat(display.hiddenLabels()).containsExactly("summary");
        assertThat(display.showsLabelOf("summary")).isFalse();
        assertThat(display.showsLabelOf("byline")).isTrue();
    }

    @Test
    void aFieldTheDisplayLeavesOutIsNotRead() {
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("summary", StringFormatter.ID, 0))
                .withoutField("byline"));

        assertThat(render(ViewDisplayConfig.DEFAULT_MODE, report()).select(".field-item"))
                .extracting(element -> element.text())
                .containsExactly("A short summary");
    }

    @Test
    void aFieldPlacedAgainComesBackOutOfTheDisabledRegion() {
        ViewDisplayConfig display = ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .withoutField("byline")
                .with(FieldDisplaySlot.of("byline", StringFormatter.ID, 0));

        assertThat(display.disabled()).isEmpty();
    }

    @Test
    void disablingAFieldTwiceLeavesItDisabledOnce() {
        ViewDisplayConfig display = ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .withoutField("byline")
                .withoutField("byline");

        assertThat(display.disabled()).containsExactly("byline");
    }

    @Test
    void changingAFormatterAndLabelInTheTeaserLeavesTheFullDisplayAlone() {
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.FULL_MODE)
                .with(FieldDisplaySlot.of("summary", StringFormatter.ID, 0))
                .withoutField("byline"));
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.TEASER_MODE)
                .with(FieldDisplaySlot.of("summary", BasicStringFormatter.ID, 0))
                .withoutLabel("summary")
                .withoutField("byline"));

        Document teaser = render(ViewDisplayConfig.TEASER_MODE, Map.of("summary", "First\nSecond"));
        Document full = render(ViewDisplayConfig.FULL_MODE, Map.of("summary", "First\nSecond"));

        assertThat(teaser.select("br")).hasSize(1);
        assertThat(teaser.select(".field-label")).isEmpty();
        assertThat(full.select("br")).isEmpty();
        assertThat(full.selectFirst(".field-label").text()).isEqualTo("Summary");
    }

    @Test
    void everyReferenceFormatterAnswersToItsOwnName() {
        assertThat(new EntityReferenceLabelFormatter(entities, entityTypeManager).id())
                .isEqualTo(EntityReferenceLabelFormatter.ID);
        assertThat(new EntityReferenceIdFormatter().id()).isEqualTo(EntityReferenceIdFormatter.ID);
        assertThat(new EntityReferenceViewFormatter(entities, displays).id())
                .isEqualTo(EntityReferenceViewFormatter.ID);
    }

    @Test
    void aFieldTheDisplayPlacesNowhereSitsAtTheFront() {
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("byline", StringFormatter.ID, 10)));

        assertThat(render(ViewDisplayConfig.DEFAULT_MODE, report()).select(".field-label"))
                .extracting(element -> element.text())
                .containsExactly("Summary", "Byline");
    }

    @Test
    void placingAFieldLeavesAnotherFieldsDisabledPlacementAlone() {
        ViewDisplayConfig display = ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .withoutField("byline")
                .with(FieldDisplaySlot.of("summary", StringFormatter.ID, 0));

        assertThat(display.disabled()).containsExactly("byline");
    }

    @Test
    void aDisplayThatWasDeletedIsGone() {
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.TEASER_MODE));

        displays.delete("story", BUNDLE, ViewDisplayConfig.TEASER_MODE);

        assertThat(displays.find("story", BUNDLE, ViewDisplayConfig.TEASER_MODE)).isEmpty();
    }

    @Test
    void aReferenceReadsAsItsTargetsLabelLinkedToIt() {
        entities.save(EntityData.of("story", 1L, BUNDLE, "The first report", Map.of()));
        attach(new FieldStorageConfig("related", "story", EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, "story")), "Related");
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("related", EntityReferenceLabelFormatter.ID, 0))
                .withoutField("summary")
                .withoutField("byline"));

        Document document = render(ViewDisplayConfig.DEFAULT_MODE, Map.of("related", 1L));

        assertThat(document.selectFirst("a").attr("href")).isEqualTo("/story/1");
        assertThat(document.selectFirst("a").text()).isEqualTo("The first report");
    }

    @Test
    void aReferenceCanReadAsAnUnlinkedLabelOrAsTheBareId() {
        entities.save(EntityData.of("story", 1L, BUNDLE, "The first report", Map.of()));
        attach(new FieldStorageConfig("related", "story", EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, "story")), "Related");

        Document unlinked = Jsoup.parseBodyFragment(renderRelated(
                Map.of(FieldFormatterManager.FORMATTER_SETTING, EntityReferenceLabelFormatter.ID,
                        EntityReferenceLabelFormatter.LINK, false)));
        Document bare = Jsoup.parseBodyFragment(renderRelated(
                Map.of(FieldFormatterManager.FORMATTER_SETTING, EntityReferenceIdFormatter.ID)));

        assertThat(unlinked.select("a")).isEmpty();
        assertThat(unlinked.selectFirst(".field-item").text()).isEqualTo("The first report");
        assertThat(bare.selectFirst(".field-item").text()).isEqualTo("1");
    }

    @Test
    void aReferenceToSomethingThatIsGoneReadsAsItsId() {
        attach(new FieldStorageConfig("related", "story", EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, "story")), "Related");

        String label = renderRelated(Map.of(
                FieldFormatterManager.FORMATTER_SETTING, EntityReferenceLabelFormatter.ID));
        String rendered = renderRelated(Map.of(
                FieldFormatterManager.FORMATTER_SETTING, EntityReferenceViewFormatter.ID));

        assertThat(Jsoup.parseBodyFragment(label).selectFirst(".field-item").text()).isEqualTo("404");
        assertThat(Jsoup.parseBodyFragment(rendered).selectFirst(".field-item").text()).isEqualTo("404");
    }

    @Test
    void aRenderedReferenceShowsItsTargetInTheViewModeTheDisplayNames() {
        entities.save(EntityData.of("story", 1L, BUNDLE, "The first report",
                Map.of("summary", "A short summary", "byline", "By Alice")));
        attach(new FieldStorageConfig("related", "story", EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, "story")), "Related");
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.TEASER_MODE)
                .with(FieldDisplaySlot.of("summary", StringFormatter.ID, 0))
                .withoutField("byline")
                .withoutField("related"));
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(new FieldDisplaySlot("related", EntityReferenceViewFormatter.ID, 0,
                        Map.of(EntityReferenceViewFormatter.VIEW_MODE, ViewDisplayConfig.TEASER_MODE)))
                .withoutField("summary")
                .withoutField("byline"));

        Document document = render(ViewDisplayConfig.DEFAULT_MODE, Map.of("related", 1L));

        assertThat(document.selectFirst(".referenced-entity .field-item").text())
                .isEqualTo("A short summary");
        assertThat(document.text()).doesNotContain("By Alice");
    }

    @Test
    void anEntityThatRefersToItselfIsNotRenderedTwice() {
        attach(new FieldStorageConfig("related", "story", EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, "story")), "Related");
        entities.save(EntityData.of("story", 1L, BUNDLE, "The first report",
                Map.of("summary", "A short summary")));
        entities.save(EntityData.of("story", 1L, BUNDLE, "The first report",
                Map.of("summary", "A short summary", "related", 1L)));
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("summary", StringFormatter.ID, 0))
                .with(new FieldDisplaySlot("related", EntityReferenceViewFormatter.ID, 10,
                        Map.of(EntityReferenceViewFormatter.VIEW_MODE, ViewDisplayConfig.DEFAULT_MODE)))
                .withoutField("byline"));

        Document document = render(ViewDisplayConfig.DEFAULT_MODE,
                Map.of("summary", "A short summary", "related", 1L));

        assertThat(document.select(".referenced-entity")).hasSize(1);
        assertThat(document.select(".referenced-entity .referenced-entity")).isEmpty();
    }

    private String renderRelated(Map<String, Object> formatterSettings) {
        displays.save(ViewDisplayConfig.of("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(new FieldDisplaySlot("related",
                        formatterSettings.get(FieldFormatterManager.FORMATTER_SETTING).toString(),
                        0, formatterSettings))
                .withoutField("summary")
                .withoutField("byline"));
        return displays.render("story", BUNDLE, ViewDisplayConfig.DEFAULT_MODE,
                Map.of("related", entities.load("story", 1L).map(entity -> entity.id()).orElse(404L)));
    }

    @TestConfiguration
    static class StoryTypes {

        @Bean
        EntityTypeProvider storyTypes() {
            return () -> List.of(STORY, STORY_TYPE);
        }
    }
}
