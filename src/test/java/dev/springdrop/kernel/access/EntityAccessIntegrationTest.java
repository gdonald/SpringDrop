package dev.springdrop.kernel.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityAccessRule;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQuery;
import dev.springdrop.kernel.entity.query.EntityQueryAccessFilter;
import dev.springdrop.kernel.entity.query.EntityQueryAccessRule;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.BooleanFieldType;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@SpringBootTest
@AutoConfigureMockMvc
class EntityAccessIntegrationTest extends AbstractIntegrationTest {

    record Bulletin(long id, String label) {
    }

    record BulletinType(String id, String label) {
    }

    static final String VIEW_DRAFTS = "view draft bulletins";

    static final EntityType BULLETIN = EntityType.content("bulletin", Bulletin.class)
            .withBundles("type", "bulletin_type");

    static final EntityType BULLETIN_TYPE = EntityType.config("bulletin_type", BulletinType.class);

    private static final String BUNDLE = "note";

    @Autowired
    private EntityAccessManager entityAccess;

    @Autowired
    private AccessHelper access;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void twoBulletinsOnePublishedOneDraft() {
        entityTypeManager.installStorage("bulletin");
        bundleManager.save("bulletin", new BundleDefinition(BUNDLE, "Note"));
        fields.createStorage(FieldStorageConfig.single("published", "bulletin", BooleanFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of("published", "bulletin", BUNDLE, "Published"));

        entities.save(EntityData.of("bulletin", 1L, BUNDLE, "Out in the open", Map.of("published", true)));
        entities.save(EntityData.of("bulletin", 2L, BUNDLE, "Still a draft", Map.of("published", false)));
    }

    @AfterEach
    void removeBulletins() {
        List.of(1L, 2L).forEach(id -> entities.delete("bulletin", id));
        fields.fieldNames("bulletin", BUNDLE).forEach(field -> fields.deleteStorage("bulletin", field));
        bundleManager.delete("bulletin", BUNDLE);
    }

    private static Authentication reader(String... permissions) {
        return new TestingAuthenticationToken("reader", "password", permissions);
    }

    private EntityData bulletin(long id) {
        return entities.load("bulletin", id).orElseThrow();
    }

    @Test
    void aRuleAllowsWhatTheTypeAloneWouldNot() {
        assertThat(entityAccess.check("bulletin", bulletin(1L), EntityAccessHandler.VIEW, reader()).allowed())
                .isTrue();
    }

    @Test
    void aRuleForbidsWhatTheReaderMayNotSee() {
        AccessResult draft = entityAccess.check("bulletin", bulletin(2L), EntityAccessHandler.VIEW, reader());

        assertThat(draft.allowed()).isFalse();
        assertThat(draft.forbidden()).isTrue();
        assertThat(draft.reason()).isEqualTo("Only published bulletins are readable");
    }

    @Test
    void thePermissionOpensWhatTheRuleWouldOtherwiseHide() {
        assertThat(entityAccess
                .check("bulletin", bulletin(2L), EntityAccessHandler.VIEW, reader(VIEW_DRAFTS)).allowed())
                .isTrue();
    }

    @Test
    void theTypesOwnHandlerStillAnswersForOperationsNoRuleSpeaksTo() {
        assertThat(entityAccess
                .check("bulletin", bulletin(1L), EntityAccessHandler.DELETE, reader("administer bulletin")).allowed())
                .isTrue();
        assertThat(entityAccess
                .check("bulletin", bulletin(1L), EntityAccessHandler.DELETE, reader()).allowed())
                .isFalse();
    }

    @Test
    void aDecisionSaysNothingWhenNobodyHasAnOpinion() {
        AccessResult neutral = AccessResult.neutral();

        assertThat(neutral.neutralDecision()).isTrue();
        assertThat(neutral.allowed()).isFalse();
        assertThat(neutral.forbidden()).isFalse();
        assertThat(neutral.or(AccessResult.neutral()).neutralDecision()).isTrue();
        assertThat(neutral.and(AccessResult.allow()).neutralDecision()).isTrue();
    }

    @Test
    void oneForbiddingSettlesItHoweverManyAllow() {
        assertThat(AccessResult.allow().or(AccessResult.forbid()).forbidden()).isTrue();
        assertThat(AccessResult.forbid().or(AccessResult.allow()).forbidden()).isTrue();
        assertThat(AccessResult.neutral().or(AccessResult.allow()).allowed()).isTrue();
        assertThat(AccessResult.allow().and(AccessResult.allow()).allowed()).isTrue();
    }

    @Test
    void aReasonSurvivesBeingCombined() {
        AccessResult refused = AccessResult.forbid("Not yours to read");

        assertThat(refused.or(AccessResult.neutral()).reason()).isEqualTo("Not yours to read");
        assertThat(AccessResult.neutral().or(refused).reason()).isEqualTo("Not yours to read");
        assertThat(AccessResult.allow().because("Yours").reason()).isEqualTo("Yours");
    }

    @Test
    void aQueryTaggedForAccessLeavesOutWhatTheReaderMayNotSee() {
        EntityQuery query = queries.query("bulletin")
                .inLanguage(EntityData.DEFAULT_LANGCODE)
                .accessTag(EntityQueryAccessFilter.accessTagFor("bulletin"));

        assertThat(query.ids()).containsExactly(1L);
    }

    @Test
    void anUntaggedQueryIsLeftAsItWasAsked() {
        assertThat(queries.query("bulletin").inLanguage(EntityData.DEFAULT_LANGCODE).ids())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void aQueryTaggedForAnotherTypeIsLeftAlone() {
        assertThat(queries.query("bulletin")
                .inLanguage(EntityData.DEFAULT_LANGCODE)
                .accessTag(EntityQueryAccessFilter.accessTagFor("something_else"))
                .ids())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void theControllerHidesABulletinTheReaderMayNotSee() throws Exception {
        mockMvc.perform(get("/bulletin/1").with(user("reader"))).andExpect(status().isOk());
        mockMvc.perform(get("/bulletin/2").with(user("reader"))).andExpect(status().isForbidden());
    }

    @Test
    void theTemplateLeavesOutAnEditButtonTheReaderCannotUse() throws Exception {
        Document allowed = pageFor("/bulletin/1", "administer bulletin");
        Document refused = pageFor("/bulletin/1");

        assertThat(allowed.selectFirst("a.btn")).isNotNull();
        BootstrapAssertions.assertEditControlsAreButtons(allowed);
        assertThat(refused.select("a.btn")).isEmpty();
    }

    @Test
    void theHelperAnswersTheChecksATemplateAsks() {
        assertThat(access.has("nothing at all")).isFalse();
        assertThat(access.mayView("bulletin", bulletin(1L))).isTrue();
        assertThat(access.mayView("bulletin", bulletin(2L))).isFalse();
        assertThat(access.mayUpdate("bulletin", bulletin(1L))).isFalse();
        assertThat(access.mayDelete("bulletin", bulletin(1L))).isFalse();
        assertThat(access.mayCreate("bulletin")).isFalse();
    }

    private Document pageFor(String path, String... permissions) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(user("reader").authorities(
                        java.util.Arrays.stream(permissions)
                                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                .toArray(org.springframework.security.core.GrantedAuthority[]::new))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @TestConfiguration
    static class BulletinModule {

        @Bean
        EntityTypeProvider bulletinTypes() {
            return () -> List.of(BULLETIN, BULLETIN_TYPE);
        }

        /** Anyone may read a published bulletin; a draft needs the permission. */
        @Bean
        EntityAccessRule bulletinAccess() {
            return (type, entity, operation, authentication) -> {
                if (!"bulletin".equals(type.id()) || !EntityAccessHandler.VIEW.equals(operation)) {
                    return AccessResult.neutral();
                }
                boolean published = entity instanceof EntityData bulletin
                        && Boolean.TRUE.equals(bulletin.fields().get("published"));
                if (published) {
                    return AccessResult.allow();
                }
                boolean mayReadDrafts = authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(VIEW_DRAFTS));
                return mayReadDrafts
                        ? AccessResult.allow()
                        : AccessResult.forbid("Only published bulletins are readable");
            };
        }

        @Bean
        EntityQueryAccessRule bulletinQueryAccess() {
            return new EntityQueryAccessRule() {
                @Override
                public String entityTypeId() {
                    return "bulletin";
                }

                @Override
                public void narrow(EntityQuery query, org.springframework.security.core.Authentication auth) {
                    query.condition(Condition.equal("published", true));
                }
            };
        }

        @Bean
        RouteRegistrar bulletinRoutes() {
            return () -> List.of(RouteDefinition.frontEnd("/bulletin/**", "bulletin_page", "Bulletin"));
        }

        @Controller
        static class BulletinController {

            private final EntityCrudService entities;
            private final EntityAccessManager entityAccess;

            BulletinController(EntityCrudService entities, EntityAccessManager entityAccess) {
                this.entities = entities;
                this.entityAccess = entityAccess;
            }

            @GetMapping("/bulletin/{id}")
            String show(@PathVariable long id, Model model) {
                EntityData bulletin = entities.load("bulletin", id).orElseThrow();
                if (!entityAccess.may("bulletin", bulletin, EntityAccessHandler.VIEW)) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            entityAccess.check("bulletin", bulletin, EntityAccessHandler.VIEW).reason());
                }
                model.addAttribute("bulletin", bulletin);
                return "bulletin/page";
            }
        }
    }
}
