package dev.springdrop.kernel.access;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryAccessFilter;
import dev.springdrop.kernel.entity.query.EntityQueryAccessRule;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.user.AccountCancellationService;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.CancellationMethod;
import dev.springdrop.kernel.user.LoginFloodGuard;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The corners of the access, flood, and cancellation work: what happens with
 * nobody signed in, with the first account signed in, and with content that
 * records no owner.
 */
@SpringBootTest
class AccessEdgeCaseTest extends AbstractIntegrationTest {

    record Leaflet(long id, String label) {
    }

    /** Content that records nobody, so cancellation has nothing to act on here. */
    static final EntityType LEAFLET = EntityType.content("leaflet", Leaflet.class);

    record LeafletType(String id, String label) {
    }

    /** A config type, which owns no content and is passed over when an account closes. */
    static final EntityType LEAFLET_TYPE = EntityType.config("leaflet_type", LeafletType.class);

    /** Content that records an owner, for the query rule to narrow. */
    static final EntityType FLYER = EntityType.content("flyer", Leaflet.class)
            .withBaseFields(List.of(BaseFieldDefinition.optional(BaseFieldDefinition.OWNER, ColumnType.BIGINT)));

    @Autowired
    private AccessHelper access;

    @Autowired
    private AccountCancellationService cancellations;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private LoginFloodGuard loginGuard;

    @Autowired
    private FloodService flood;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void storageAndAClearRecord() {
        accounts.install();
        entityTypeManager.installStorage("leaflet");
        entityTypeManager.installStorage("flyer");
        SecurityContextHolder.clearContext();
        accounts.findByName("quitter").ifPresent(account -> entities.delete("user", account.id()));
        List.of(1L, 2L).forEach(id -> entities.delete("flyer", id));
        entities.delete("leaflet", 1L);
        flood.clear(FloodSettings.LOGIN_USER, "someone");
        flood.clear(FloodSettings.LOGIN_IP, "10.0.0.9");
    }

    @AfterEach
    void nobodySignedIn() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInAs(long id, String name) {
        AccountPrincipal principal = new AccountPrincipal(id, name, "", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    @Test
    void withNobodySignedInNoPermissionIsHeldAndNothingIsAllowed() {
        entities.save(new EntityData("leaflet", 1L, null, null, "A leaflet",
                EntityData.DEFAULT_LANGCODE, null, Map.of()));

        assertThat(access.has("administer everything")).isFalse();
        assertThat(access.mayView("leaflet", entities.load("leaflet", 1L).orElseThrow())).isFalse();
    }

    @Test
    void aPermissionSomeoneHoldsIsReportedAsHeld() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("holder", "",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "administer leaflet"))));

        assertThat(access.has("administer leaflet")).isTrue();
        assertThat(access.has("administer something else")).isFalse();
    }

    @Test
    void theFirstAccountIsAllowedWhateverItAsksAbout() {
        signedInAs(UserAccount.ADMINISTRATOR_ID, "admin");
        entities.save(new EntityData("leaflet", 1L, null, null, "A leaflet",
                EntityData.DEFAULT_LANGCODE, null, Map.of()));

        assertThat(access.mayUpdate("leaflet", entities.load("leaflet", 1L).orElseThrow())).isTrue();
    }

    @Test
    void theFirstAccountSeesEverythingAQueryCouldReturn() {
        entities.save(new EntityData("flyer", 1L, null, null, "Mine",
                EntityData.DEFAULT_LANGCODE, null, Map.of(BaseFieldDefinition.OWNER, 7L)));
        entities.save(new EntityData("flyer", 2L, null, null, "Someone else's",
                EntityData.DEFAULT_LANGCODE, null, Map.of(BaseFieldDefinition.OWNER, 8L)));

        assertThat(taggedFlyerIds()).containsExactly(1L);

        signedInAs(UserAccount.ADMINISTRATOR_ID, "admin");
        assertThat(taggedFlyerIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void anAccountThatIsNotTheFirstSeesOnlyWhatTheRuleAllows() {
        entities.save(new EntityData("flyer", 1L, null, null, "Mine",
                EntityData.DEFAULT_LANGCODE, null, Map.of(BaseFieldDefinition.OWNER, 7L)));
        entities.save(new EntityData("flyer", 2L, null, null, "Someone else's",
                EntityData.DEFAULT_LANGCODE, null, Map.of(BaseFieldDefinition.OWNER, 8L)));
        signedInAs(9L, "someone");

        assertThat(taggedFlyerIds()).containsExactly(1L);
    }

    @Test
    void closingAnAccountLeavesContentThatRecordsNobodyAlone() {
        UserAccount quitter = accounts.create("quitter", "quitter@example.com",
                passwordEncoder.encode("secret enough"));
        entities.save(new EntityData("leaflet", 1L, null, null, "Nobody's leaflet",
                EntityData.DEFAULT_LANGCODE, null, Map.of()));

        cancellations.cancel(quitter.id(), CancellationMethod.DELETE);

        assertThat(entities.load("leaflet", 1L)).isPresent();
        assertThat(accounts.find(quitter.id())).isEmpty();
    }

    @Test
    void anAddressThatHasTriedTooOftenIsTurnedAwayWhicheverAccountItAsksFor() {
        for (int attempt = 0; attempt <= FloodSettings.IP_THRESHOLD; attempt++) {
            flood.register(FloodSettings.LOGIN_IP, "10.0.0.9", FloodSettings.IP_WINDOW);
        }

        assertThat(loginGuard.mayAttempt("someone", "10.0.0.9")).isFalse();
        flood.clear(FloodSettings.LOGIN_IP, "10.0.0.9");
    }

    @Test
    void anAuthenticationThatIsNotAnAccountIsCheckedLikeAnyoneElse() {
        entities.save(new EntityData("flyer", 1L, null, null, "Mine",
                EntityData.DEFAULT_LANGCODE, null, Map.of(BaseFieldDefinition.OWNER, 7L)));
        entities.save(new EntityData("flyer", 2L, null, null, "Someone else's",
                EntityData.DEFAULT_LANGCODE, null, Map.of(BaseFieldDefinition.OWNER, 8L)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain", "", List.of()));

        assertThat(taggedFlyerIds()).containsExactly(1L);
        assertThat(access.has("administer everything")).isFalse();
        assertThat(access.mayCreate("flyer")).isFalse();
    }

    @Test
    void aDecisionThatSaysNothingIsNeitherAllowedNorForbidden() {
        assertThat(AccessResult.neutral().neutralDecision()).isTrue();
        assertThat(AccessResult.allow().neutralDecision()).isFalse();
        assertThat(AccessResult.allow().and(AccessResult.neutral()).neutralDecision()).isTrue();
        assertThat(AccessResult.neutral().and(AccessResult.neutral()).neutralDecision()).isTrue();
    }

    private List<Object> taggedFlyerIds() {
        return queries.query("flyer")
                .accessTag(EntityQueryAccessFilter.accessTagFor("flyer"))
                .ids();
    }

    @TestConfiguration
    static class Leaflets {

        @Bean
        EntityTypeProvider leafletTypes() {
            return () -> List.of(LEAFLET, LEAFLET_TYPE, FLYER);
        }

        /** A reader sees only the flyers credited to account 7. */
        @Bean
        EntityQueryAccessRule flyerQueryAccess() {
            return new EntityQueryAccessRule() {
                @Override
                public String entityTypeId() {
                    return "flyer";
                }

                @Override
                public void narrow(dev.springdrop.kernel.entity.query.EntityQuery query,
                        org.springframework.security.core.Authentication authentication) {
                    query.condition(Condition.equal(BaseFieldDefinition.OWNER, 7L));
                }
            };
        }
    }
}
