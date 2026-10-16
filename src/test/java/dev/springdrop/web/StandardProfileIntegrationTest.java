package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchProgress;
import dev.springdrop.kernel.block.BlockPlacement;
import dev.springdrop.kernel.block.BlockPlacementManager;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.install.InstallState;
import dev.springdrop.kernel.install.Installer;
import dev.springdrop.kernel.install.StandardProfile;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.taxonomy.VocabularyManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.workflow.WorkflowManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.MockMvc;

/** A standard install, checked against the inventory in docs/standard-profile.md. */
@SpringBootTest
@AutoConfigureMockMvc
class StandardProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Installer installer;

    @Autowired
    private BatchManager batches;

    @Autowired
    private InstallState state;

    @Autowired
    private ConfigStore config;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private VocabularyManager vocabularies;

    @Autowired
    private MenuManager menus;

    @Autowired
    private BlockPlacementManager blocks;

    @Autowired
    private RoleManager roles;

    @Autowired
    private FormDisplayManager formDisplays;

    @Autowired
    private ViewDisplayManager viewDisplays;

    @Autowired
    private ViewManager views;

    @Autowired
    private WorkflowManager workflows;

    @Autowired
    private ContentModerationService moderation;

    private Map<String, ConfigStore.StoredConfig> before;

    private EntityData administratorBefore;

    @BeforeEach
    void aStandardInstall() {
        before = new LinkedHashMap<>(config.everything());
        administratorBefore = entities.load(UserEntityType.ID, UserAccount.ADMINISTRATOR_ID).orElseThrow();
        BatchProgress progress = batches.processAll(installer.start(installer.profile(StandardProfile.ID)
                .orElseThrow(), new Installer.SiteDetails("en", "Riverside News", "web@riverside.example", "editor",
                "editor@riverside.example", "{noop}a long password"), "/").batchId());
        assertThat(progress.errors()).isEmpty();
    }

    /** Puts every config object back as it was before the install, dropping the field tables it made. */
    @AfterEach
    void undoTheInstall() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        Map<String, ConfigStore.StoredConfig> after = config.everything();
        after.keySet().stream().filter(name -> !before.containsKey(name))
                .filter(name -> name.startsWith(FieldStorageConfig.CONFIG_PREFIX + "."))
                .forEach(name -> fields.deleteStorage(NodeEntityType.ID, name.substring(name.lastIndexOf('.') + 1)));
        config.everything().keySet().stream().filter(name -> !before.containsKey(name)).forEach(config::delete);
        before.forEach((name, stored) -> config.saveImported(name, stored.data(), stored.uuid()));
        entities.save(administratorBefore);
        state.forget();
    }

    @Test
    void aStandardInstallMatchesTheInventory() {
        assertThat(types.find("article").orElseThrow().promoted()).isTrue();
        assertThat(types.find("page").orElseThrow().promoted()).isFalse();
        assertThat(fields.fieldNames(NodeEntityType.ID, "article"))
                .containsExactlyInAnyOrder("body", "field_image", "field_tags", "comment");
        assertThat(fields.fieldNames(NodeEntityType.ID, "page")).containsExactly("body");
        assertThat(fields.findStorage(NodeEntityType.ID, "field_tags").orElseThrow().unlimited()).isTrue();
        assertThat(vocabularies.find("tags")).isPresent();
        assertThat(List.of("main", "admin", "footer", "account"))
                .allSatisfy(menu -> assertThat(menus.find(menu)).isPresent());
        assertThat(blocks.all().stream().filter(block -> block.theme().equals("front-end")).map(BlockPlacement::plugin))
                .contains("system_main_block", "help_block", "system_menu_block:account",
                        "system_menu_block:footer", "system_powered_by_block");
        assertThat(roles.find("anonymous").orElseThrow().permissions()).contains("access content",
                "use text format restricted_html");
        assertThat(roles.find("authenticated").orElseThrow().permissions()).contains("post comments",
                "use text format basic_html");
        assertThat(roles.find("content_editor").orElseThrow().permissions()).contains("create article content",
                "edit own page content");
        assertThat(roles.find("administrator").orElseThrow().administrator()).isTrue();
        assertThat(formDisplays.find(NodeEntityType.ID, "article", "default").orElseThrow().slots())
                .containsOnlyKeys("body", "field_image", "field_tags", "comment");
        assertThat(viewDisplays.find(NodeEntityType.ID, "article", "teaser").orElseThrow().disabled())
                .contains("comment");
        assertThat(viewDisplays.find(NodeEntityType.ID, "page", "default")).isPresent();
        assertThat(views.find("content")).isPresent();
        assertThat(views.find("people")).isPresent();
        assertThat(workflows.find(ContentModerationService.EDITORIAL)).isPresent();
        assertThat(moderation.moderationOf("article")).isEmpty();
    }

    @Test
    void aStandardInstallExposesArticleAndBasicPageWithTheirFieldsAndDisplays() throws Exception {
        long article = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, "article", "Library hours change",
                Map.of(BaseFieldDefinition.STATUS, true, "body", Map.of("value", "<p>The library opens at nine.</p>",
                        "summary", "", "format", "basic_html"))), UserAccount.ADMINISTRATOR_ID).id()).longValue();
        var editor = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "editor", "", true, List.of()));

        Document form = Jsoup.parse(mockMvc.perform(get("/node/add/article").with(editor)).andReturn().getResponse()
                .getContentAsString());
        assertThat(form.select("[name^=body]")).isNotEmpty();
        assertThat(form.select("[name^=field_image]")).isNotEmpty();
        assertThat(form.select("[name^=field_tags]")).isNotEmpty();
        Document page = Jsoup.parse(mockMvc.perform(get("/node/add/page").with(editor)).andReturn().getResponse()
                .getContentAsString());
        assertThat(page.select("[name^=body]")).isNotEmpty();
        assertThat(page.select("[name^=field_tags]")).isEmpty();
        Document shown = Jsoup.parse(mockMvc.perform(get(NodeEntityType.path(article)).with(editor)).andReturn()
                .getResponse().getContentAsString());
        assertThat(shown.text()).contains("Library hours change", "The library opens at nine.");
    }
}
