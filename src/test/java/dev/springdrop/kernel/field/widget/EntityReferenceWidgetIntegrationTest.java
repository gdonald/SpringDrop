package dev.springdrop.kernel.field.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.widget.types.EntityReferenceAutocompleteWidget;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.support.AbstractIntegrationTest;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EntityReferenceWidgetIntegrationTest extends AbstractIntegrationTest {

    record Post(long id, String label) {
    }

    record PostType(String id, String label) {
    }

    record Topic(long id, String label) {
    }

    static final EntityType POST = EntityType.content("post", Post.class)
            .withBundles("type", "post_type");

    static final EntityType POST_TYPE = EntityType.config("post_type", PostType.class);

    /** Topics only an editor may see, so the suggestion list can be checked. */
    static final EntityType TOPIC = EntityType.content("topic", Topic.class)
            .withAccessHandler(EditorsOnly.class);

    private static final String BUNDLE = "article";

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void aPostBundleReferencingTopics() {
        entityTypeManager.installStorage("post");
        entityTypeManager.installStorage("topic");
        bundleManager.save("post", new BundleDefinition(BUNDLE, "Article"));
        entities.save(topic(1L, "Gardening"));
        entities.save(topic(2L, "Cooking"));
    }

    @AfterEach
    void removeFieldsBundleAndTopics() {
        fields.fieldNames("post", BUNDLE).forEach(field -> fields.deleteStorage("post", field));
        bundleManager.delete("post", BUNDLE);
        entityTypeManager.storageFor("topic").load(TOPIC, 3L)
                .ifPresent(row -> entities.delete("topic", 3L));
        entities.delete("topic", 1L);
        entities.delete("topic", 2L);
    }

    private static EntityData topic(Long id, String label) {
        return new EntityData("topic", id, null, null, label, EntityData.DEFAULT_LANGCODE, null, Map.of());
    }

    private WidgetContext attach(Map<String, Object> instanceSettings) {
        fields.createStorage(new FieldStorageConfig("topic", "post", EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, "topic")));
        fields.createInstance(FieldInstanceConfig.of("topic", "post", BUNDLE, "Topic")
                .withSettings(instanceSettings));
        return widgets.context("post", BUNDLE, "topic");
    }

    private Document render(WidgetContext context, List<Object> values) {
        return Jsoup.parseBodyFragment(renderer.render(widgets.build(context, values, 0)));
    }

    @Test
    void aReferenceFieldGetsAnAutocompleteInput() {
        WidgetContext context = attach(Map.of());

        Document document = render(context, List.of());

        assertThat(widgets.widget(context).id()).isEqualTo(EntityReferenceAutocompleteWidget.ID);
        assertThat(document.selectFirst("input").attr("hx-get")).isEqualTo(FieldWidgetPaths.AUTOCOMPLETE);
        assertThat(document.selectFirst("input").attr("list")).isEqualTo("topic-suggestions");
    }

    @Test
    void anExistingReferenceIsShownAsItsLabelWithItsId() {
        WidgetContext context = attach(Map.of());

        assertThat(render(context, List.of(1L)).selectFirst("input").attr("value"))
                .isEqualTo("Gardening (1)");
    }

    @Test
    void aReferenceToSomethingThatIsNoLongerThereShowsTheBareId() {
        WidgetContext context = attach(Map.of());

        assertThat(render(context, List.of(404L)).selectFirst("input").attr("value")).isEqualTo("404");
    }

    @Test
    void pickingASuggestionStoresTheIdOutOfIt() {
        WidgetContext context = attach(Map.of());

        assertThat(widgets.extract(context, Map.of("topic", "Gardening (1)"))).containsExactly(1L);
    }

    @Test
    void aBareIdIsStoredAsItStands() {
        WidgetContext context = attach(Map.of());

        assertThat(widgets.extract(context, Map.of("topic", "2"))).containsExactly(2L);
    }

    @Test
    void anEmptyReferenceIsLeftUnset() {
        WidgetContext context = attach(Map.of());

        assertThat(widgets.extract(context, Map.of("topic", "  "))).isEmpty();
    }

    @Test
    void aReferenceThatWasNotSubmittedAtAllIsUnset() {
        WidgetContext context = attach(Map.of());

        assertThat(widgets.extract(context, Map.of())).isEmpty();
    }

    @Test
    void aConfigEntityTypeHasNoIdToAllocate() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> entityTypeManager.storageFor("post_type").nextId(POST_TYPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("post_type");
    }

    @Test
    void theFirstEntityOfATypeIsGivenTheFirstId() {
        entities.delete("topic", 1L);
        entities.delete("topic", 2L);

        assertThat(entityTypeManager.storageFor("topic").nextId(TOPIC)).isEqualTo(1L);
    }

    @Test
    void aNameThatMatchesNothingIsKeptForTheServerToRejectWhenTaggingIsOff() {
        WidgetContext context = attach(Map.of());

        assertThat(widgets.extract(context, Map.of("topic", "Nothing like it")))
                .containsExactly("Nothing like it");
    }

    @Test
    void aFreeTaggingFieldCreatesTheTargetItWasGivenTheNameOf() {
        WidgetContext context = attach(Map.of(EntityReferenceAutocompleteWidget.AUTO_CREATE, true));

        List<Object> values = widgets.extract(context, Map.of("topic", "Beekeeping"));

        assertThat(values).hasSize(1);
        assertThat(entities.load("topic", values.getFirst()))
                .hasValueSatisfying(created -> assertThat(created.label()).isEqualTo("Beekeeping"));
    }

    @Test
    void aCreatedTargetIsGivenTheBundleTheFieldNames() {
        WidgetContext context = attach(Map.of(
                EntityReferenceAutocompleteWidget.AUTO_CREATE, true,
                EntityReferenceAutocompleteWidget.AUTO_CREATE_BUNDLE, "topic"));

        List<Object> values = widgets.extract(context, Map.of("topic", "Beekeeping"));

        assertThat(entities.load("topic", values.getFirst())).isPresent();
    }

    @Test
    void suggestionsAreOfferedToSomeoneWhoMaySeeThem() throws Exception {
        attach(Map.of());

        String list = suggestions("Garden", user("editor").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("edit topics")));

        assertThat(Jsoup.parseBodyFragment(list).select("option"))
                .extracting(option -> option.attr("value"))
                .containsExactly("Gardening (1)");
    }

    @Test
    void nothingIsSuggestedToSomeoneWhoMayNotSeeTheTargets() throws Exception {
        attach(Map.of());

        assertThat(Jsoup.parseBodyFragment(suggestions("Garden", user("visitor")))
                .select("option")).isEmpty();
    }

    @Test
    void everyTargetIsSuggestedWhenNothingHasBeenTypedYet() throws Exception {
        attach(Map.of());

        String list = suggestions("", user("editor").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("edit topics")));

        assertThat(Jsoup.parseBodyFragment(list).select("option"))
                .extracting(option -> option.attr("value"))
                .containsExactly("Cooking (2)", "Gardening (1)");
    }

    private String suggestions(String typed, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {

        return mockMvc.perform(get(FieldWidgetPaths.AUTOCOMPLETE)
                        .param(FieldWidgetController.ENTITY_TYPE, "post")
                        .param(FieldWidgetController.BUNDLE, BUNDLE)
                        .param(FieldWidgetController.FIELD, "topic")
                        .param("q", typed)
                        .with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @TestConfiguration
    static class PostTypes {

        @Bean
        EntityTypeProvider postTypes() {
            return () -> List.of(POST, POST_TYPE, TOPIC);
        }

        @Bean
        EditorsOnly editorsOnly() {
            return new EditorsOnly();
        }
    }

    /** Only someone who can edit topics may see one. */
    static class EditorsOnly implements EntityAccessHandler {

        @Override
        public AccessResult check(
                EntityType type, Object entity, String operation, Authentication authentication) {

            boolean granted = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("edit topics"));
            return granted ? AccessResult.allow() : AccessResult.forbid();
        }
    }
}
