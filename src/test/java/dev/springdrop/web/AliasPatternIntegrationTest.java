package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.path.AliasPattern;
import dev.springdrop.kernel.path.AliasPatternManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class AliasPatternIntegrationTest extends AbstractIntegrationTest {

    private static final String BLOG = "pattern_blog";

    private static final long EDITH = 121L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AliasPatternManager patterns;

    @Autowired
    private PathAliasManager aliases;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private MenuLinkContentService menuLinks;

    @BeforeEach
    void aBlogWithAPattern() {
        menuLinks.install();
        nodeTypes.save(NodeType.of(BLOG, "Blog post"));
        patterns.save(new AliasPattern(NodeEntityType.ID, BLOG, "/blog/[node:created:year]/[node:title]", false));
    }

    @AfterEach
    void removeEverything() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        patterns.all().forEach(pattern -> patterns.delete(pattern.entityType(), pattern.bundle()));
        nodeTypes.delete(BLOG);
        aliases.all().forEach(alias -> aliases.deleteAll(alias.source()));
    }

    private static RequestPostProcessor editor() {
        return user(new AccountPrincipal(EDITH, "edith", "", true, List.of(NodePermissions.create(BLOG),
                NodePermissions.editOwn(BLOG), NodePermissions.ACCESS_CONTENT)));
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(AliasPatternController.ADMINISTER_URL_ALIASES));
    }

    private MockHttpServletResponse submit(String path, Map<String, String> fields, RequestPostProcessor who)
            throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(csrf());
        fields.forEach(request::param);
        return mockMvc.perform(request.with(who)).andReturn().getResponse();
    }

    private long create(String title, boolean automatic, String typed) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>(Map.of("title", title, "path", typed));
        if (automatic) {
            fields.put(NodeController.PATH_AUTO, FormRenderer.CHECKED_VALUE);
        }
        MockHttpServletResponse response = submit(NodeController.ADD_PATH + "/" + BLOG, fields, editor());
        return Long.parseLong(response.getRedirectedUrl().substring("/node/".length()));
    }

    private Optional<String> aliasOf(long id) {
        return aliases.aliasOf(NodeEntityType.path(id), EntityData.DEFAULT_LANGCODE);
    }

    private static String year() {
        return String.valueOf(Year.now().getValue());
    }

    @Test
    void creatingANodeMakesATransliteratedAliasFromItsPattern() throws Exception {
        long id = create("Crème Brûlée, the Recipe!", true, "");

        assertThat(aliasOf(id)).contains("/blog/" + year() + "/creme-brulee-the-recipe");
        assertThat(aliases.generated(NodeEntityType.path(id), EntityData.DEFAULT_LANGCODE)).isTrue();
        assertThat(mockMvc.perform(get("/blog/" + year() + "/creme-brulee-the-recipe").with(editor())).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anAliasAnotherPageHasIsNumbered() throws Exception {
        long first = create("Opening hours", true, "");
        long second = create("Opening hours", true, "");
        long third = create("Opening hours", true, "");

        assertThat(List.of(aliasOf(first), aliasOf(second), aliasOf(third))).containsExactly(
                Optional.of("/blog/" + year() + "/opening-hours"), Optional.of("/blog/" + year() + "/opening-hours-1"),
                Optional.of("/blog/" + year() + "/opening-hours-2"));
    }

    @Test
    void anAliasIsKeptWhenTheTitleChangesUnlessThePatternSaysToMakeItAgain() throws Exception {
        long id = create("Opening hours", true, "");
        Map<String, String> edit = Map.of("title", "Summer hours", NodeController.PATH_AUTO,
                FormRenderer.CHECKED_VALUE);

        submit(NodeEntityType.editPath(id), edit, editor());
        assertThat(aliasOf(id)).contains("/blog/" + year() + "/opening-hours");
        patterns.save(new AliasPattern(NodeEntityType.ID, BLOG, "/blog/[node:title]", true));
        submit(NodeEntityType.editPath(id), edit, editor());

        assertThat(aliasOf(id)).contains("/blog/summer-hours");
    }

    @Test
    void aTypedAliasIsKeptUntilAutomaticAliasesAreChosenAgain() throws Exception {
        long id = create("Opening hours", false, "/hours");
        assertThat(aliases.generated(NodeEntityType.path(id), EntityData.DEFAULT_LANGCODE)).isFalse();
        Document form = Jsoup.parse(mockMvc.perform(get(NodeEntityType.editPath(id)).with(editor())).andReturn()
                .getResponse().getContentAsString());
        assertThat(form.selectFirst("input[name=path_auto]").hasAttr("checked")).isFalse();

        submit(NodeEntityType.editPath(id), Map.of("title", "Opening hours", NodeController.PATH_AUTO,
                FormRenderer.CHECKED_VALUE, "path", "/ignored"), editor());

        assertThat(aliasOf(id)).contains("/blog/" + year() + "/opening-hours");
        Document again = Jsoup.parse(mockMvc.perform(get(NodeEntityType.editPath(id)).with(editor())).andReturn()
                .getResponse().getContentAsString());
        assertThat(again.selectFirst("input[name=path_auto]").hasAttr("checked")).isTrue();
    }

    @Test
    void aNewNodeOfATypeWithAPatternStartsWithAutomaticAliases() throws Exception {
        Document form = Jsoup.parse(mockMvc.perform(get(NodeController.ADD_PATH + "/" + BLOG).with(editor()))
                .andReturn().getResponse().getContentAsString());

        assertThat(form.selectFirst("input[name=path_auto]").hasAttr("checked")).isTrue();
        assertThat(form.selectFirst("input[name=path]").attr("data-state-disabled")).isEqualTo("path_auto:true");
    }

    @Test
    void aNodeWithoutAnAliasOffersAutomaticAliasesWhenEdited() throws Exception {
        long id = create("Opening hours", false, "");

        Document form = Jsoup.parse(mockMvc.perform(get(NodeEntityType.editPath(id)).with(editor())).andReturn()
                .getResponse().getContentAsString());

        assertThat(aliasOf(id)).isEmpty();
        assertThat(form.selectFirst("input[name=path_auto]").hasAttr("checked")).isTrue();
    }

    @Test
    void anAutomaticAliasSkipsTheTypedOnesChecks() throws Exception {
        long id = create("Opening hours", true, "not an alias");

        assertThat(aliasOf(id)).contains("/blog/" + year() + "/opening-hours");
    }

    @Test
    void aPatternMakingNothingGivesNoAlias() throws Exception {
        patterns.save(new AliasPattern(NodeEntityType.ID, BLOG, "/[node:missing]", false));

        assertThat(aliasOf(create("Opening hours", true, ""))).isEmpty();
    }

    @Test
    void aPatternsOwnTextIsMadeFitForAPathAndLongAliasesAreCut() throws Exception {
        patterns.save(new AliasPattern(NodeEntityType.ID, BLOG, "/our blog!/[node:title]", false));
        long tidy = create("Hours", true, "");
        String longTitle = "abcdefghi ".repeat(25);
        long first = create(longTitle, true, "");
        long second = create(longTitle, true, "");

        assertThat(aliasOf(tidy)).contains("/our-blog-/hours");
        assertThat(aliasOf(first).orElseThrow()).hasSizeLessThanOrEqualTo(PathAliasManager.MAX_LENGTH)
                .doesNotEndWith("-");
        assertThat(aliasOf(second).orElseThrow()).hasSize(PathAliasManager.MAX_LENGTH).endsWith("-1");
    }

    @Test
    void anAliasThatIsAPageOfTheSiteIsNumbered() throws Exception {
        patterns.save(new AliasPattern(NodeEntityType.ID, BLOG, "/[node:title]", false));

        assertThat(aliasOf(create("Node", true, ""))).contains("/node-1");
    }

    @Test
    void theNodeFormOfATypeWithoutAPatternHasNoAutomaticChoice() throws Exception {
        patterns.delete(NodeEntityType.ID, BLOG);

        Document form = Jsoup.parse(mockMvc.perform(get(NodeController.ADD_PATH + "/" + BLOG).with(editor()))
                .andReturn().getResponse().getContentAsString());

        assertThat(form.select("input[name=path_auto]")).isEmpty();
    }

    @Test
    void patternsAreListedAddedEditedAndDeleted() throws Exception {
        nodeTypes.save(NodeType.of("pattern_page", "Pattern page"));
        try {
            Document list = Jsoup.parse(mockMvc.perform(get(AliasPatternController.PATH).with(administrator()))
                    .andReturn().getResponse().getContentAsString());
            assertThat(list.selectFirst("tr[data-alias-pattern=" + BLOG + "]").text()).contains("Blog post")
                    .contains("/blog/[node:created:year]/[node:title]").contains("No");
            assertThat(mockMvc.perform(get(AliasPatternController.PATH + "/add").with(administrator())).andReturn()
                    .getResponse().getContentAsString()).contains("Pattern page");

            submit(AliasPatternController.PATH + "/add", Map.of("bundle", "pattern_page", "pattern",
                    "/pages/[node:title]", "regenerate", "true"), administrator());
            assertThat(patterns.find(NodeEntityType.ID, "pattern_page"))
                    .contains(new AliasPattern(NodeEntityType.ID, "pattern_page", "/pages/[node:title]", true));

            assertThat(mockMvc.perform(get(AliasPatternController.PATH + "/manage/pattern_page")
                    .with(administrator())).andReturn().getResponse().getContentAsString())
                    .contains("Content type: Pattern page");
            submit(AliasPatternController.PATH + "/manage/pattern_page", Map.of("pattern", "/p/[node:id]"),
                    administrator());
            assertThat(patterns.find(NodeEntityType.ID, "pattern_page")).map(AliasPattern::pattern)
                    .contains("/p/[node:id]");

            assertThat(mockMvc.perform(get(AliasPatternController.PATH + "/manage/pattern_page/delete")
                    .with(administrator())).andReturn().getResponse().getContentAsString())
                    .contains("Delete the URL alias pattern of Pattern page?");
            submit(AliasPatternController.PATH + "/manage/pattern_page/delete", Map.of(), administrator());
            assertThat(patterns.find(NodeEntityType.ID, "pattern_page")).isEmpty();
        } finally {
            nodeTypes.delete("pattern_page");
        }
    }

    @Test
    void aPatternWrittenWronglyOrForATypeTheSiteLacksIsRefused() throws Exception {
        MockHttpServletResponse wrong = submit(AliasPatternController.PATH + "/add",
                Map.of("bundle", BLOG, "pattern", "blog/[node:title]"), administrator());
        MockHttpServletResponse unknown = submit(AliasPatternController.PATH + "/add",
                Map.of("bundle", "retired", "pattern", "/x"), administrator());
        MockHttpServletResponse editedWrong = submit(AliasPatternController.PATH + "/manage/" + BLOG,
                Map.of("pattern", "/blog posts"), administrator());
        MockHttpServletResponse unchosen = submit(AliasPatternController.PATH + "/add",
                Map.of("bundle", "", "pattern", "/x"), administrator());

        assertThat(wrong.getContentAsString()).contains("Start the pattern with /");
        assertThat(unknown.getContentAsString()).contains("Choose a content type.");
        assertThat(editedWrong.getContentAsString()).contains("Start the pattern with /");
        assertThat(unchosen.getContentAsString()).contains("This value is required.")
                .doesNotContain("Choose a content type.");
    }

    @Test
    void aPatternTheSiteLacksIsNotFoundAndTheListNeedsThePermission() throws Exception {
        patterns.save(new AliasPattern("taxonomy_term", "tags", "/tags/[term:name]", false));
        nodeTypes.delete(BLOG);

        Document list = Jsoup.parse(mockMvc.perform(get(AliasPatternController.PATH).with(administrator()))
                .andReturn().getResponse().getContentAsString());

        assertThat(list.select("tr[data-alias-pattern]")).extracting(row -> row.attr("data-alias-pattern"))
                .containsExactly(BLOG);
        assertThat(list.selectFirst("tr[data-alias-pattern]").text()).contains(BLOG);
        assertThat(mockMvc.perform(get(AliasPatternController.PATH + "/manage/retired").with(administrator()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(AliasPatternController.PATH).with(editor())).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get(AliasPatternController.PATH + "/manage/" + BLOG + "/delete")
                .with(administrator())).andReturn().getResponse().getContentAsString())
                .contains("Delete the URL alias pattern of " + BLOG + "?");
        assertThat(mockMvc.perform(get(AliasPatternController.PATH + "/manage/" + BLOG).with(administrator()))
                .andReturn().getResponse().getContentAsString()).contains("Content type: " + BLOG);
    }
}
