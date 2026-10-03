package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.block.BlockInstance;
import dev.springdrop.kernel.block.plugins.FieldBlock;
import dev.springdrop.kernel.block.plugins.FieldBlockDeriver;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.formatter.types.StringFormatter;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.layout.LayoutBuilderDisplay;
import dev.springdrop.kernel.layout.LayoutDisplayManager;
import dev.springdrop.kernel.layout.Section;
import dev.springdrop.kernel.layout.SectionComponent;
import dev.springdrop.kernel.layout.layouts.OneColumnLayout;
import dev.springdrop.kernel.layout.layouts.TwoColumnLayout;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class NodeLayoutOverrideIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String SUBTITLE = "subtitle";

    private static final String SUMMARY = "summary";

    private static final String FULL = ViewDisplayConfig.FULL_MODE;

    private static final long AUTHOR = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private LayoutDisplayManager layoutDisplays;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private EntityData overridden;

    private EntityData sibling;

    @BeforeEach
    void twoArticlesSharingAnOverridableLayout() {
        menus.install();
        storedLinks.install();
        nodes.install();
        clearContent();
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(new FieldStorageConfig(SUBTITLE, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUBTITLE, NodeEntityType.ID, ARTICLE, "Subtitle"));
        fields.createStorage(new FieldStorageConfig(SUMMARY, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
        layoutDisplays.save(LayoutBuilderDisplay.of(NodeEntityType.ID, ARTICLE, FULL, List.of(
                        Section.of(OneColumnLayout.ID).withComponent(fieldBlock("subtitle-block", SUBTITLE))))
                .withOverridesAllowed(true));
        overridden = article("Spring schedule");
        sibling = article("Summer schedule");
    }

    @AfterEach
    void noContentLeft() {
        clearContent();
    }

    private void clearContent() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        List.of(SUBTITLE, SUMMARY).forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        layoutDisplays.delete(NodeEntityType.ID, ARTICLE, FULL);
        layoutDisplays.delete(UserEntityType.ID, UserEntityType.ID, FULL);
        types.all().forEach(type -> types.delete(type.id()));
    }

    private static SectionComponent fieldBlock(String id, String field) {
        return new SectionComponent(OneColumnLayout.CONTENT, 0, BlockInstance.of(id,
                FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, field), field).withoutLabel());
    }

    private EntityData article(String title) {
        Map<String, Object> values = new LinkedHashMap<>(
                nodes.create(types.find(ARTICLE).orElseThrow(), AUTHOR).fields());
        values.put(SUBTITLE, "From the front desk");
        values.put(SUMMARY, "Opening hours change in May.");
        return nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title, values), AUTHOR);
    }

    private static RequestPostProcessor layoutEditor() {
        return user(new AccountPrincipal(AUTHOR, "author", "", true, List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.editOwn(ARTICLE), Permissions.CONFIGURE_LAYOUT_OVERRIDES)));
    }

    private static String layoutOf(EntityData node) {
        return NodeLayoutController.layoutPath(node.id());
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void redirects(MockHttpServletRequestBuilder request, String to) throws Exception {
        mockMvc.perform(request.with(layoutEditor()).with(csrf())).andExpect(redirectedUrl(to));
    }

    private EntityData reloaded(EntityData node) {
        return nodes.find(((Number) node.id()).longValue()).orElseThrow();
    }

    private void overrideWithTwoColumns(EntityData node) throws Exception {
        redirects(post(layoutOf(node) + "/section/add").param(LayoutEditor.LAYOUT, TwoColumnLayout.ID), layoutOf(node));
        redirects(post(layoutOf(node) + "/section/1/region/second/add/"
                        + FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY))
                .param(LayoutEditor.LABEL, "Summary")
                .param(FieldBlock.FORMATTER_ELEMENT, StringFormatter.ID), layoutOf(node));
    }

    @Test
    void overridingOneNodesLayoutChangesOnlyThatNode() throws Exception {
        overrideWithTwoColumns(overridden);

        Document changed = page(NodeEntityType.path(overridden.id()), layoutEditor());
        Document unchanged = page(NodeEntityType.path(sibling.id()), layoutEditor());

        assertThat(changed.select(".node__content .layout").eachAttr("data-layout"))
                .containsExactly(OneColumnLayout.ID, TwoColumnLayout.ID);
        assertThat(changed.selectFirst(".node__content [data-layout=" + TwoColumnLayout.ID + "] [data-region=second]")
                .text()).isEqualTo("Opening hours change in May.");
        assertThat(unchanged.select(".node__content .layout").eachAttr("data-layout"))
                .containsExactly(OneColumnLayout.ID);
    }

    @Nested
    class EditingANodesLayout {

        @Test
        void theEditorStartsFromTheContentTypesLayout() throws Exception {
            Document document = page(layoutOf(overridden), layoutEditor());

            assertThat(document.select("tr[data-block=subtitle-block]")).isNotEmpty();
            assertThat(document.select("button:contains(Revert)")).isEmpty();
            assertThat(layoutDisplays.overrideOf(reloaded(overridden))).isEmpty();
        }

        @Test
        void theFirstChangeSavesACopyOnTheNodeAndOffersARevert() throws Exception {
            redirects(post(layoutOf(overridden)).param(LayoutEditor.WEIGHT_PREFIX + "subtitle-block", "4"),
                    layoutOf(overridden));

            assertThat(layoutDisplays.overrideOf(reloaded(overridden))).hasValueSatisfying(sections ->
                    assertThat(sections.getFirst().component("subtitle-block").orElseThrow().weight()).isEqualTo(4));
            assertThat(page(layoutOf(overridden), layoutEditor()).select("button:contains(Revert)")).isNotEmpty();
            assertThat(layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL).orElseThrow().sections().getFirst()
                    .component("subtitle-block").orElseThrow().weight()).isZero();
        }

        @Test
        void revertingPutsTheNodeBackOnTheContentTypesLayout() throws Exception {
            overrideWithTwoColumns(overridden);

            redirects(post(layoutOf(overridden) + "/revert"), layoutOf(overridden));

            assertThat(layoutDisplays.overrideOf(reloaded(overridden))).isEmpty();
            assertThat(page(NodeEntityType.path(overridden.id()), layoutEditor())
                    .select(".node__content .layout").eachAttr("data-layout")).containsExactly(OneColumnLayout.ID);
        }

        @Test
        void eachChangeKeepsARevisionAndAnEarlierRevisionKeepsTheLayoutItHad() throws Exception {
            long before = reloaded(overridden).revisionId();

            overrideWithTwoColumns(overridden);

            EntityData current = reloaded(overridden);
            assertThat(current.revisionId()).isGreaterThan(before);
            assertThat(layoutDisplays.overrideOf(current)).isPresent();
            assertThat(entities.load(NodeEntityType.ID, overridden.id(), EntityData.DEFAULT_LANGCODE, before))
                    .hasValueSatisfying(earlier -> assertThat(layoutDisplays.overrideOf(earlier)).isEmpty());
        }

        @Test
        void eachPartOfTheEditorWorksOnTheNodesOwnLayout() throws Exception {
            String layout = layoutOf(overridden);
            assertThat(page(layout + "/section/0/region/content/library", layoutEditor()).select("tbody code")
                    .eachText()).contains(FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY));
            assertThat(page(layout + "/section/0/region/content/add/"
                    + FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY), layoutEditor())
                    .select("input[name=" + LayoutEditor.LABEL + "]")).isNotEmpty();
            assertThat(page(layout + "/block/subtitle-block", layoutEditor())
                    .selectFirst("input[name=" + LayoutEditor.LABEL + "]").val()).isEqualTo(SUBTITLE);

            redirects(post(layout + "/block/subtitle-block").param(LayoutEditor.LABEL, "Standfirst")
                    .param(FieldBlock.FORMATTER_ELEMENT, StringFormatter.ID), layout);
            assertThat(layoutDisplays.overrideOf(reloaded(overridden)).orElseThrow().getFirst()
                    .component("subtitle-block").orElseThrow().block().label()).isEqualTo("Standfirst");

            redirects(post(layout + "/block/subtitle-block/remove"), layout);
            assertThat(layoutDisplays.overrideOf(reloaded(overridden)).orElseThrow().getFirst().components())
                    .isEmpty();

            redirects(post(layout + "/section/0/remove"), layout);
            assertThat(layoutDisplays.overrideOf(reloaded(overridden))).isEmpty();
        }
    }

    @Nested
    class WhoMayOverride {

        @Test
        void theLayoutTabShowsOnlyToSomeoneWhoMayChangeTheLayout() throws Exception {
            RequestPostProcessor editorWithoutPermission = user(new AccountPrincipal(AUTHOR, "author", "", true,
                    List.of(NodePermissions.ACCESS_CONTENT, NodePermissions.editOwn(ARTICLE))));

            assertThat(page(NodeEntityType.path(overridden.id()), layoutEditor())
                    .select("a[href=" + layoutOf(overridden) + "]")).isNotEmpty();
            assertThat(page(NodeEntityType.path(overridden.id()), editorWithoutPermission)
                    .select("a[href=" + layoutOf(overridden) + "]")).isEmpty();
        }

        @Test
        void someoneWhoMayNotConfigureOverridesIsRefused() throws Exception {
            mockMvc.perform(get(layoutOf(overridden)).with(user(new AccountPrincipal(AUTHOR, "author", "", true,
                            List.of(NodePermissions.editOwn(ARTICLE))))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void someoneWhoMayNotEditTheNodeIsRefused() throws Exception {
            mockMvc.perform(get(layoutOf(overridden)).with(user(new AccountPrincipal(8L, "other", "", true,
                            List.of(NodePermissions.editOwn(ARTICLE), Permissions.CONFIGURE_LAYOUT_OVERRIDES)))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aNodeWhoseTypeDoesNotAllowOverridesHasNoLayoutOfItsOwn() throws Exception {
            layoutDisplays.save(layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL).orElseThrow()
                    .withOverridesAllowed(false));

            mockMvc.perform(get(layoutOf(overridden)).with(layoutEditor())).andExpect(status().isNotFound());
        }

        @Test
        void anUnknownNodeHasNoLayout() throws Exception {
            mockMvc.perform(get(NodeLayoutController.layoutPath(999_999)).with(layoutEditor()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aStoredLayoutIsSetAsideOnceTheTypeStopsAllowingOverrides() throws Exception {
            overrideWithTwoColumns(overridden);
            layoutDisplays.save(layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL).orElseThrow()
                    .withOverridesAllowed(false));

            assertThat(page(NodeEntityType.path(overridden.id()), layoutEditor())
                    .select(".node__content .layout").eachAttr("data-layout")).containsExactly(OneColumnLayout.ID);
        }
    }

    @Nested
    class AllowingOverrides {

        private final String defaults = LayoutBuilderController.layoutPath(NodeEntityType.ID, ARTICLE, FULL);

        private RequestPostProcessor fieldAdministrator() {
            return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_FIELDS));
        }

        @Test
        void theContentTypesLayoutOffersToLetEachNodeHaveItsOwn() throws Exception {
            Document document = page(defaults, fieldAdministrator());

            assertThat(document.selectFirst("input[name=" + LayoutBuilderController.ALLOW_OVERRIDES + "]")
                    .hasAttr("checked")).isTrue();
        }

        @Test
        void savingWithoutTheChoiceStopsAllowingOverrides() throws Exception {
            mockMvc.perform(post(defaults).with(fieldAdministrator()).with(csrf())).andExpect(redirectedUrl(defaults));

            assertThat(layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL).orElseThrow().allowOverrides()).isFalse();
        }

        @Test
        void savingWithTheChoiceAllowsOverrides() throws Exception {
            layoutDisplays.save(layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL).orElseThrow()
                    .withOverridesAllowed(false));

            mockMvc.perform(post(defaults).with(fieldAdministrator()).with(csrf())
                    .param(LayoutBuilderController.ALLOW_OVERRIDES, "on")).andExpect(redirectedUrl(defaults));

            assertThat(layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL).orElseThrow().allowOverrides()).isTrue();
        }

        @Test
        void aTypeWithNowhereToKeepALayoutNeverAllowsOverrides() throws Exception {
            String users = LayoutBuilderController.layoutPath(UserEntityType.ID, UserEntityType.ID, FULL);
            mockMvc.perform(post(users + "/enable").with(fieldAdministrator()).with(csrf()));

            assertThat(page(users, fieldAdministrator())
                    .select("input[name=" + LayoutBuilderController.ALLOW_OVERRIDES + "]")).isEmpty();
            mockMvc.perform(post(users).with(fieldAdministrator()).with(csrf())
                    .param(LayoutBuilderController.ALLOW_OVERRIDES, "on"));
            assertThat(layoutDisplays.find(UserEntityType.ID, UserEntityType.ID, FULL).orElseThrow().allowOverrides())
                    .isFalse();
        }
    }

    @Test
    void anEmptyStoredLayoutMeansTheNodeUsesItsTypes() {
        EntityData reverted = layoutDisplays.withoutOverride(reloaded(overridden));

        assertThat(reverted.fields()).containsEntry(LayoutDisplayManager.OVERRIDE_FIELD, List.of());
        assertThat(layoutDisplays.overrideOf(reverted)).isEmpty();
        assertThat(reloaded(sibling).fields()).doesNotContainKey(LayoutDisplayManager.OVERRIDE_FIELD)
                .containsKey(BaseFieldDefinition.STATUS);
    }
}
