package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchProgress;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.install.DemoProfile;
import dev.springdrop.kernel.install.InstallState;
import dev.springdrop.kernel.install.Installer;
import dev.springdrop.kernel.media.MediaEntityType;
import dev.springdrop.kernel.menu.MenuLinkContentEntityType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserEntityType;
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

@SpringBootTest
@AutoConfigureMockMvc
class DemoProfileIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> CONTENT_TYPES = List.of(NodeEntityType.ID, MediaEntityType.ID,
            TaxonomyEntityType.ID, MenuLinkContentEntityType.ID);

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
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    private Map<String, ConfigStore.StoredConfig> before;

    private EntityData administratorBefore;

    @BeforeEach
    void aDemoInstall() {
        before = new LinkedHashMap<>(config.everything());
        administratorBefore = entities.load(UserEntityType.ID, UserAccount.ADMINISTRATOR_ID).orElseThrow();
        BatchProgress progress = batches.processAll(installer.start(installer.profile(DemoProfile.ID).orElseThrow(),
                new Installer.SiteDetails("en", "Kitchen Notes", "web@kitchen.example", "cook",
                        "cook@kitchen.example", "{noop}a long password"), "/").batchId());
        assertThat(progress.errors()).isEmpty();
    }

    /** Removes the sample content and puts every config object back as it was before the install. */
    @AfterEach
    void undoTheInstall() {
        CONTENT_TYPES.forEach(type -> queries.query(type).ids().forEach(id -> entities.delete(type, id)));
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
        config.everything().keySet().stream().filter(name -> !before.containsKey(name))
                .filter(name -> name.startsWith(FieldStorageConfig.CONFIG_PREFIX + ".")).forEach(name -> {
                    String[] parts = name.split("\\.");
                    fields.deleteStorage(parts[2], parts[3]);
                });
        config.everything().keySet().stream().filter(name -> !before.containsKey(name)).forEach(config::delete);
        before.forEach((name, stored) -> config.saveImported(name, stored.data(), stored.uuid()));
        entities.save(administratorBefore);
        state.forget();
    }

    @Test
    void aDemoInstallRendersAPopulatedFrontPageAndASampleArticleWithMedia() throws Exception {
        Document front = Jsoup.parse(mockMvc.perform(get(SiteInformation.DEFAULT_FRONT_PAGE)).andReturn().getResponse()
                .getContentAsString());
        assertThat(front.text()).contains("Overnight sourdough loaf", "Roasted squash soup", "Apple and oat crumble",
                "not for production");

        Object loaf = queries.query(NodeEntityType.ID).condition(Condition.equal("label", "Overnight sourdough loaf"))
                .ids().getFirst();
        Document article = Jsoup.parse(mockMvc.perform(get(NodeEntityType.path(loaf))).andReturn().getResponse()
                .getContentAsString());

        assertThat(article.selectFirst("img[alt=Overnight sourdough loaf]")).isNotNull();
        assertThat(article.text()).contains("Baking", "Vegetarian");
        assertThat(queries.query(MediaEntityType.ID).count()).isEqualTo(3);
        assertThat(article.text()).contains("About this demo");
    }
}
