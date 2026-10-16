package dev.springdrop.kernel.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.table;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.maintenance.Maintenance;
import dev.springdrop.kernel.maintenance.MaintenanceSettings;
import dev.springdrop.kernel.module.ModuleManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.web.UpdateController;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UpdateIntegrationTest.FoundationUpdates.class)
class UpdateIntegrationTest extends AbstractIntegrationTest {

    static final String SETTINGS = "foundation.settings";

    static final AtomicInteger MOVES = new AtomicInteger();

    static final AtomicBoolean BREAK_SECOND = new AtomicBoolean();

    /** Two updates of the foundation test module: one moving a setting to a new key, and one that can fail. */
    @TestConfiguration
    static class FoundationUpdates {

        @Bean
        UpdateRoutine moveDepthToLevels(ConfigStore config) {
            return routine(9001, "Moves the depth setting to levels.", () -> {
                MOVES.incrementAndGet();
                Map<?, ?> stored = config.read(SETTINGS, Map.class, Map.of());
                Map<String, Object> moved = new LinkedHashMap<>();
                stored.forEach((key, value) -> moved.put(key.equals("depth") ? "levels" : String.valueOf(key), value));
                config.save(SETTINGS, moved);
            });
        }

        @Bean
        UpdateRoutine addWidth() {
            return routine(9002, "Adds a width.", () -> {
                if (BREAK_SECOND.get()) {
                    throw new IllegalStateException("The width cannot be added.");
                }
            });
        }

        static UpdateRoutine routine(int version, String description, Runnable work) {
            return new UpdateRoutine() {

                @Override
                public String module() {
                    return "foundation";
                }

                @Override
                public int version() {
                    return version;
                }

                @Override
                public String description() {
                    return description;
                }

                @Override
                public void run() {
                    work.run();
                }
            };
        }
    }

    @Autowired
    private UpdateManager updates;

    @Autowired
    private ModuleManager modules;

    @Autowired
    private ConfigStore config;

    @Autowired
    private Maintenance maintenance;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void foundationInstalledBeforeTheseUpdates() {
        clear();
        modules.enable("foundation");
        dsl.update(table("module_update")).set(DSL.field("version"), 9000).where(DSL.field("module").eq("foundation"))
                .execute();
        config.save(SETTINGS, Map.of("depth", 3));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        MOVES.set(0);
        BREAK_SECOND.set(false);
        config.delete(SETTINGS);
        config.delete(MaintenanceSettings.CONFIG_NAME);
        dsl.deleteFrom(table("module")).execute();
        dsl.deleteFrom(table("module_update")).execute();
    }

    @Test
    void aModuleUpdateRoutineMigratesExistingDataIsRecordedAndIsSkippedOnReRun() {
        assertThat(updates.pending()).extracting(UpdateRoutine::version).containsExactly(9001, 9002);

        UpdateManager.Report report = updates.runPending();

        assertThat(report.applied()).extracting(UpdateRoutine::version).containsExactly(9001, 9002);
        assertThat(report.failed()).isEmpty();
        assertThat(config.readStored(SETTINGS).orElseThrow().get("levels").asInt()).isEqualTo(3);
        assertThat(config.readStored(SETTINGS).orElseThrow().has("depth")).isFalse();
        assertThat(updates.lastApplied("foundation")).contains(9002);

        UpdateManager.Report again = updates.runPending();

        assertThat(again.applied()).isEmpty();
        assertThat(MOVES.get()).isEqualTo(1);
    }

    @Test
    void aFailingRoutineStopsTheRunAndStaysPending() {
        BREAK_SECOND.set(true);
        maintenance.save(new MaintenanceSettings(false, "Back soon."));

        UpdateManager.Report report = updates.runPending();

        assertThat(report.applied()).extracting(UpdateRoutine::version).containsExactly(9001);
        assertThat(report.failed()).map(UpdateRoutine::version).contains(9002);
        assertThat(report.reason()).isEqualTo("The width cannot be added.");
        assertThat(updates.lastApplied("foundation")).contains(9001);
        assertThat(updates.pending()).extracting(UpdateRoutine::version).containsExactly(9002);
        assertThat(maintenance.settings().enabled()).isFalse();
    }

    @Test
    void aModuleInstalledWithItsRoutinesStartsCurrent() {
        dsl.deleteFrom(table("module")).execute();
        dsl.deleteFrom(table("module_update")).execute();

        modules.enable("foundation");

        assertThat(updates.lastApplied("foundation")).contains(9002);
        assertThat(updates.pending()).isEmpty();
    }

    @Test
    void markingCurrentRecordsEveryRoutineAsApplied() {
        updates.markCurrent();

        assertThat(updates.pending()).isEmpty();
        assertThat(updates.lastApplied("content")).isEmpty();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theUpdatePageListsWhatIsPendingAndAppliesIt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        RequestPostProcessor administrator = user("admin").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_SOFTWARE_UPDATES));

        Document pending = Jsoup.parse(mockMvc.perform(get(UpdateController.PATH).session(session)
                .with(administrator)).andReturn().getResponse().getContentAsString());
        assertThat(pending.select("tr[data-update]").eachAttr("data-update"))
                .containsExactly("foundation:9001", "foundation:9002");

        mockMvc.perform(post(UpdateController.PATH).session(session).with(administrator).with(csrf()));
        Document done = Jsoup.parse(mockMvc.perform(get(UpdateController.PATH).session(session)
                .with(administrator)).andReturn().getResponse().getContentAsString());

        assertThat(done.selectFirst(".status-messages .alert-success").text()).isEqualTo("Applied 2 updates.");
        assertThat(done.select(".admin-listing-empty").text()).isEqualTo("No pending updates.");
    }

    @Test
    void theUpdatePageReportsAFailure() throws Exception {
        BREAK_SECOND.set(true);
        MockHttpSession session = new MockHttpSession();
        RequestPostProcessor administrator = user("admin").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_SOFTWARE_UPDATES));

        mockMvc.perform(post(UpdateController.PATH).session(session).with(administrator).with(csrf()));
        Document page = Jsoup.parse(mockMvc.perform(get(UpdateController.PATH).session(session)
                .with(administrator)).andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst(".status-messages .alert-success").text()).isEqualTo("Applied 1 update.");
        assertThat(page.selectFirst(".status-messages .alert-danger").text())
                .isEqualTo("The foundation update 9002 failed: The width cannot be added.");
    }

    @Test
    void theUpdatePageNeedsItsPermission() throws Exception {
        assertThat(mockMvc.perform(get(UpdateController.PATH).with(user("editor"))).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }

    @Test
    void aModuleThatIsNotInstalledHasNothingPending() {
        dsl.deleteFrom(table("module")).execute();

        assertThat(updates.pending()).isEmpty();
    }
}
