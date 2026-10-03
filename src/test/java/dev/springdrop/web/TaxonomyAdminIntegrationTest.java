package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.taxonomy.TaxonomyPermissions;
import dev.springdrop.kernel.taxonomy.TaxonomyService;
import dev.springdrop.kernel.taxonomy.TermTreeItem;
import dev.springdrop.kernel.taxonomy.Vocabulary;
import dev.springdrop.kernel.taxonomy.VocabularyManager;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.LinkedHashMap;
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
class TaxonomyAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String PLACES = "places";

    private static final String CODE = "code";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaxonomyService taxonomy;

    @Autowired
    private VocabularyManager vocabularies;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @BeforeEach
    void aPlacesVocabulary() {
        menus.install();
        storedLinks.install();
        clear();
        vocabularies.save(Vocabulary.of(PLACES, "Places").describedAs("Where things happen."));
        fields.createStorage(new FieldStorageConfig(CODE, TaxonomyEntityType.ID, StringFieldType.ID, 1,
                Map.of("max_length", 3)));
        fields.createInstance(FieldInstanceConfig.of(CODE, TaxonomyEntityType.ID, PLACES, "Code"));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(TaxonomyEntityType.ID).ids().forEach(id -> taxonomy.delete(((Number) id).longValue()));
        vocabularies.all().forEach(vocabulary -> vocabularies.delete(vocabulary.id()));
        fields.findStorage(TaxonomyEntityType.ID, CODE)
                .ifPresent(storage -> fields.deleteStorage(TaxonomyEntityType.ID, CODE));
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(TaxonomyPermissions.ADMINISTER_TAXONOMY),
                new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT));
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document submit(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(administrator()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void redirects(MockHttpServletRequestBuilder request, String to) throws Exception {
        mockMvc.perform(request.with(administrator()).with(csrf())).andExpect(redirectedUrl(to));
    }

    private long added(String name, long parent, int weight) throws Exception {
        redirects(post(TaxonomyController.managePath(PLACES) + "/add")
                .param(TaxonomyController.NAME, name)
                .param(TaxonomyController.PARENT, String.valueOf(parent))
                .param(TaxonomyController.WEIGHT, String.valueOf(weight))
                .param(TaxonomyController.PUBLISHED, "true"), TaxonomyController.overviewPath(PLACES));
        return taxonomy.tree(PLACES).stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow()
                .id();
    }

    private EntityData term(long id) {
        return taxonomy.find(id).orElseThrow();
    }

    @Test
    void aHierarchicalVocabularyBuildsPersistsParentAndWeightAndRendersItsTree() throws Exception {
        long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
        long france = added("France", europe, 0);
        long paris = added("Paris", france, 0);
        long asia = added("Asia", TaxonomyEntityType.ROOT, -1);

        Document overview = page(TaxonomyController.overviewPath(PLACES), administrator());
        assertThat(overview.select("tbody tr").eachAttr("data-term")).containsExactly(
                String.valueOf(asia), String.valueOf(europe), String.valueOf(france), String.valueOf(paris));
        assertThat(overview.select("tbody tr").eachAttr("data-depth")).containsExactly("0", "0", "1", "2");

        redirects(post(TaxonomyController.overviewPath(PLACES))
                .param(TaxonomyController.PARENT_PREFIX + paris, String.valueOf(europe))
                .param(TaxonomyController.WEIGHT_PREFIX + paris, "-5")
                .param(TaxonomyController.WEIGHT_PREFIX + asia, "3"), TaxonomyController.overviewPath(PLACES));

        assertThat(term(paris).fields()).containsEntry(TaxonomyEntityType.PARENT, europe)
                .containsEntry(TaxonomyEntityType.WEIGHT, -5);
        assertThat(taxonomy.tree(PLACES)).extracting(TermTreeItem::name)
                .containsExactly("Europe", "Paris", "France", "Asia");
    }

    @Nested
    class TheOverview {

        @Test
        void eachTermsParentChoiceLeavesOutTheTermAndTheTermsBelowIt() throws Exception {
            long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
            long france = added("France", europe, 0);
            added("Asia", TaxonomyEntityType.ROOT, 1);

            Document overview = page(TaxonomyController.overviewPath(PLACES), administrator());

            assertThat(overview.select("select[name=" + TaxonomyController.PARENT_PREFIX + europe + "] option")
                    .eachText()).containsExactly("<root>", "Asia");
            assertThat(overview.select("select[name=" + TaxonomyController.PARENT_PREFIX + france + "] option")
                    .eachText()).containsExactly("<root>", "Europe", "Asia");
            BootstrapAssertions.assertNoOutlineButtons(overview);
            BootstrapAssertions.assertEditControlsAreButtons(overview);
        }

        @Test
        void aParentThatWouldBreakTheTreeOrAWeightThatIsNotANumberLeavesThatPartAsItWas() throws Exception {
            long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
            long france = added("France", europe, 0);

            redirects(post(TaxonomyController.overviewPath(PLACES))
                    .param(TaxonomyController.PARENT_PREFIX + europe, String.valueOf(france))
                    .param(TaxonomyController.WEIGHT_PREFIX + europe, "2")
                    .param(TaxonomyController.PARENT_PREFIX + france, "upward")
                    .param(TaxonomyController.WEIGHT_PREFIX + france, "heavy"),
                    TaxonomyController.overviewPath(PLACES));

            assertThat(term(europe).fields()).containsEntry(TaxonomyEntityType.PARENT, TaxonomyEntityType.ROOT)
                    .containsEntry(TaxonomyEntityType.WEIGHT, 2);
            assertThat(term(france).fields()).containsEntry(TaxonomyEntityType.PARENT, europe)
                    .containsEntry(TaxonomyEntityType.WEIGHT, 0);
        }

        @Test
        void aVocabularyWithNoTermsSaysSo() throws Exception {
            assertThat(page(TaxonomyController.overviewPath(PLACES), administrator()).text())
                    .contains("This vocabulary has no terms yet.");
        }
    }

    @Nested
    class Terms {

        @Test
        void aTermWithoutANameOrWithAFieldValueTooLongIsNotAdded() throws Exception {
            Document document = submit(post(TaxonomyController.managePath(PLACES) + "/add")
                    .param(TaxonomyController.NAME, "")
                    .param(TaxonomyController.PARENT, "0"));
            Document tooLong = submit(post(TaxonomyController.managePath(PLACES) + "/add")
                    .param(TaxonomyController.NAME, "France")
                    .param(TaxonomyController.PARENT, "0")
                    .param(CODE, "FRAN"));

            assertThat(document.selectFirst("input[name=" + TaxonomyController.NAME + "]").hasClass("is-invalid"))
                    .isTrue();
            assertThat(tooLong.selectFirst("input[name=" + CODE + "]").hasClass("is-invalid")).isTrue();
            assertThat(taxonomy.termsIn(PLACES)).isEmpty();
        }

        @Test
        void aParentThatIsNotATermIdPutsTheTermAtTheTop() throws Exception {
            redirects(post(TaxonomyController.managePath(PLACES) + "/add")
                    .param(TaxonomyController.NAME, "Europe")
                    .param(TaxonomyController.PARENT, "nowhere"), TaxonomyController.overviewPath(PLACES));

            assertThat(taxonomy.tree(PLACES)).extracting(TermTreeItem::parent)
                    .containsExactly(TaxonomyEntityType.ROOT);
        }

        @Test
        void theAddFormOffersEveryTermAsAParent() throws Exception {
            added("Europe", TaxonomyEntityType.ROOT, 0);

            assertThat(page(TaxonomyController.managePath(PLACES) + "/add", administrator())
                    .select("select[name=" + TaxonomyController.PARENT + "] option").eachText())
                    .containsExactly("<root>", "Europe");
        }

        @Test
        void aTermIsShownOnItsOwnPageWithItsDescriptionAndFields() throws Exception {
            long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
            EntityData term = term(europe);
            Map<String, Object> values = new LinkedHashMap<>(term.fields());
            values.put(TaxonomyEntityType.DESCRIPTION, "A continent.");
            values.put(CODE, "EU");
            taxonomy.save(term.withFields(values));

            Document document = page(TaxonomyEntityType.path(europe), administrator());

            assertThat(document.selectFirst("h1").text()).isEqualTo("Europe");
            assertThat(document.selectFirst(".taxonomy-term").text()).contains("A continent.", "EU");
            assertThat(document.select("a[href=" + TaxonomyEntityType.path(europe) + "/edit]")).isNotEmpty();
        }

        @Test
        void aTermIsEditedThroughItsFormAndCannotBeMovedUnderItsOwnChild() throws Exception {
            long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
            long france = added("France", europe, 0);
            assertThat(page(TaxonomyEntityType.path(europe) + "/edit", administrator())
                    .select("select[name=" + TaxonomyController.PARENT + "] option").eachText())
                    .containsExactly("<root>");

            Document refused = submit(post(TaxonomyEntityType.path(europe) + "/edit")
                    .param(TaxonomyController.NAME, "Europe")
                    .param(TaxonomyController.PARENT, String.valueOf(france)));
            assertThat(refused.text()).contains("A term cannot sit under itself or a term below it.");

            redirects(post(TaxonomyEntityType.path(france) + "/edit")
                    .param(TaxonomyController.NAME, "La France")
                    .param(TaxonomyController.DESCRIPTION, "A country.")
                    .param(TaxonomyController.PARENT, "0")
                    .param(TaxonomyController.WEIGHT, "4"), TaxonomyEntityType.path(france));

            assertThat(term(france).label()).isEqualTo("La France");
            assertThat(term(france).fields()).containsEntry(TaxonomyEntityType.PARENT, TaxonomyEntityType.ROOT)
                    .containsEntry(TaxonomyEntityType.WEIGHT, 4)
                    .containsEntry(BaseFieldDefinition.STATUS, false);
        }

        @Test
        void aTermIsDeletedOnceConfirmedWithTheTermsBelowIt() throws Exception {
            long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
            added("France", europe, 0);
            assertThat(page(TaxonomyEntityType.path(europe) + "/delete", administrator())
                    .select("button[type=submit]").text()).isEqualTo("Delete term");

            redirects(post(TaxonomyEntityType.path(europe) + "/delete"), TaxonomyController.overviewPath(PLACES));

            assertThat(taxonomy.termsIn(PLACES)).isEmpty();
        }

        @Test
        void readingEditingAndDeletingGoByTheTermsAccessRules() throws Exception {
            long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
            RequestPostProcessor reader = user("reader").authorities(
                    new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT));
            RequestPostProcessor editor = user("editor").authorities(
                    new SimpleGrantedAuthority(TaxonomyPermissions.edit(PLACES)));

            assertThat(page(TaxonomyEntityType.path(europe), reader)
                    .select("a[href=" + TaxonomyEntityType.path(europe) + "/edit]")).isEmpty();
            mockMvc.perform(get(TaxonomyEntityType.path(europe) + "/edit").with(reader))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(TaxonomyEntityType.path(europe) + "/edit").with(editor)).andExpect(status().isOk());
            mockMvc.perform(get(TaxonomyEntityType.path(europe) + "/delete").with(editor))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(TaxonomyEntityType.path(999_999)).with(reader)).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Vocabularies {

        @Test
        void theVocabulariesAreClosedToSomeoneWithoutThePermission() throws Exception {
            mockMvc.perform(get(TaxonomyController.PATH).with(user("visitor"))).andExpect(status().isForbidden());
        }

        @Test
        void eachVocabularyIsListedWithItsActionsAsButtons() throws Exception {
            Document document = page(TaxonomyController.PATH, administrator());

            assertThat(document.selectFirst("[data-vocabulary=" + PLACES + "]").text())
                    .contains("Places", "Where things happen.");
            assertThat(document.selectFirst("a:contains(Manage fields)").attr("href"))
                    .isEqualTo(FieldUiController.fieldsPath(TaxonomyEntityType.ID, PLACES));
            BootstrapAssertions.assertNoOutlineButtons(document);
            BootstrapAssertions.assertEditControlsAreButtons(document);
        }

        @Test
        void anEmptyListSaysSo() throws Exception {
            vocabularies.delete(PLACES);

            assertThat(page(TaxonomyController.PATH, administrator()).text()).contains("There are no vocabularies yet.");
        }

        @Test
        void aVocabularyIsAddedWithAMachineNameFromItsName() throws Exception {
            assertThat(page(TaxonomyController.PATH + "/add", administrator())
                    .select("input[name=" + TaxonomyController.NAME + "]")).isNotEmpty();

            redirects(post(TaxonomyController.PATH + "/add").param(TaxonomyController.NAME, "Event types"),
                    TaxonomyController.overviewPath("event_types"));

            assertThat(vocabularies.find("event_types")).map(Vocabulary::label).contains("Event types");
        }

        @Test
        void aVocabularyWithoutANameIsNotAdded() throws Exception {
            Document document = submit(post(TaxonomyController.PATH + "/add").param(TaxonomyController.NAME, ""));

            assertThat(document.selectFirst("input[name=" + TaxonomyController.NAME + "]").hasClass("is-invalid"))
                    .isTrue();
            assertThat(vocabularies.all()).hasSize(1);
        }

        @Test
        void aVocabularyIsRenamed() throws Exception {
            assertThat(page(TaxonomyController.managePath(PLACES), administrator())
                    .selectFirst("input[name=" + TaxonomyController.NAME + "]").val()).isEqualTo("Places");

            redirects(post(TaxonomyController.managePath(PLACES)).param(TaxonomyController.NAME, "Locations")
                    .param(TaxonomyController.DESCRIPTION, "Where."), TaxonomyController.overviewPath(PLACES));

            assertThat(vocabularies.find(PLACES)).hasValueSatisfying(vocabulary -> {
                assertThat(vocabulary.label()).isEqualTo("Locations");
                assertThat(vocabulary.description()).isEqualTo("Where.");
            });
        }

        @Test
        void aVocabularyIsDeletedOnceConfirmedWithItsTerms() throws Exception {
            added("Europe", TaxonomyEntityType.ROOT, 0);
            assertThat(page(TaxonomyController.managePath(PLACES) + "/delete", administrator())
                    .select("button[type=submit]").text()).isEqualTo("Delete vocabulary");

            redirects(post(TaxonomyController.managePath(PLACES) + "/delete"), TaxonomyController.PATH);

            assertThat(vocabularies.find(PLACES)).isEmpty();
            assertThat(taxonomy.termsIn(PLACES)).isEmpty();
        }

        @Test
        void anUnknownVocabularyIsNotFound() throws Exception {
            mockMvc.perform(get(TaxonomyController.overviewPath("missing")).with(administrator()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void anUnpublishedTermIsClosedToReaders() throws Exception {
        long europe = added("Europe", TaxonomyEntityType.ROOT, 0);
        EntityData term = term(europe);
        Map<String, Object> values = new LinkedHashMap<>(term.fields());
        values.put(BaseFieldDefinition.STATUS, false);
        taxonomy.save(term.withFields(values));

        mockMvc.perform(get(TaxonomyEntityType.path(europe)).with(user("reader").authorities(
                        new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT))))
                .andExpect(status().isForbidden());
    }
}
