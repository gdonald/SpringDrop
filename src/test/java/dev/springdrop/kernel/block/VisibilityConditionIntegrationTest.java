package dev.springdrop.kernel.block;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.block.conditions.EntityBundleCondition;
import dev.springdrop.kernel.block.conditions.EntityBundleConditionDeriver;
import dev.springdrop.kernel.block.conditions.LanguageCondition;
import dev.springdrop.kernel.block.conditions.RequestPathCondition;
import dev.springdrop.kernel.block.conditions.UserRoleCondition;
import dev.springdrop.kernel.block.content.BlockContentEntityType;
import dev.springdrop.kernel.block.content.BlockContentService;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class VisibilityConditionIntegrationTest extends AbstractIntegrationTest {

    private static final String EDITOR = "editor";

    private static final String BLOCK_BUNDLES = EntityBundleConditionDeriver.ID + ":" + BlockContentEntityType.ID;

    private static final BlockContext MINUTES = BlockContext.of("/minutes/7", "Board minutes");

    @Autowired
    private VisibilityConditionManager conditions;

    @Autowired
    private RoleManager roles;

    @Autowired
    private BlockContentService library;

    @BeforeEach
    void rolesAndBlockTypes() {
        roles.install();
        roles.save(RoleConfig.of(EDITOR, "Editor", 5));
        library.saveType(new BundleDefinition("notice", "Notice"));
        library.saveType(new BundleDefinition("promotion", "Promotion"));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void theSiteAsItWas() {
        roles.delete(EDITOR);
        library.deleteType("notice");
        library.deleteType("promotion");
        SecurityContextHolder.clearContext();
        LocaleContextHolder.resetLocaleContext();
    }

    private static void signedInHolding(String... roleIds) {
        AccountPrincipal principal = new AccountPrincipal(7L, "edith", "", true, List.of(), List.of(roleIds));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    private boolean passes(ConditionConfig condition, BlockContext context) {
        return conditions.passes(List.of(condition), context);
    }

    private static ConditionConfig pages(String pages) {
        return ConditionConfig.of(RequestPathCondition.ID, Map.of(RequestPathCondition.PAGES, pages));
    }

    @Test
    void theConditionsAreListedByLabel() {
        assertThat(conditions.definitions()).extracting(ConditionDefinition::label).isSorted();
        assertThat(conditions.definitions()).extracting(ConditionDefinition::id).contains(
                RequestPathCondition.ID, UserRoleCondition.ID, LanguageCondition.ID, BLOCK_BUNDLES);
    }

    @Test
    void onlyEntityTypesWithBundlesGetABundleCondition() {
        assertThat(conditions.definitions()).extracting(ConditionDefinition::id)
                .doesNotContain(EntityBundleConditionDeriver.ID + ":user");
    }

    @Test
    void aPathConditionHoldsOnAPageItListsAndNotOnOthers() {
        assertThat(passes(pages("/about\n/minutes/*"), MINUTES)).isTrue();
        assertThat(passes(pages("/about"), MINUTES)).isFalse();
    }

    @Test
    void theFrontPageIsListedByItsOwnName() {
        assertThat(passes(pages(RequestPathCondition.FRONT_PAGE), BlockContext.of("/", "Home"))).isTrue();
        assertThat(passes(pages(RequestPathCondition.FRONT_PAGE), MINUTES)).isFalse();
    }

    @Test
    void aPathPatternMatchesTheWholePathRatherThanPartOfIt() {
        assertThat(passes(pages("/minutes"), MINUTES)).isFalse();
        assertThat(passes(pages("/minutes/7.html"), BlockContext.of("/minutes/7xhtml", "Minutes"))).isFalse();
    }

    @Test
    void aNegatedConditionShowsTheBlockEverywhereExceptWhereItHolds() {
        assertThat(passes(pages("/minutes/*").negated(), MINUTES)).isFalse();
        assertThat(passes(pages("/about").negated(), MINUTES)).isTrue();
    }

    @Test
    void everyConditionAPlacementCarriesHasToAgree() {
        signedInHolding(EDITOR);
        ConditionConfig editors = ConditionConfig.of(UserRoleCondition.ID, Map.of(UserRoleCondition.ROLES, List.of(EDITOR)));

        assertThat(conditions.passes(List.of(pages("/minutes/*"), editors), MINUTES)).isTrue();
        assertThat(conditions.passes(List.of(pages("/about"), editors), MINUTES)).isFalse();
    }

    @Test
    void aPlacementWithNoConditionsShowsEverywhere() {
        assertThat(conditions.passes(List.of(), MINUTES)).isTrue();
    }

    @Test
    void aConditionWhosePluginIsGoneHidesTheBlockAndDependsOnNothing() {
        ConditionConfig vanished = ConditionConfig.of("vanished", Map.of());

        assertThat(passes(vanished, MINUTES)).isFalse();
        assertThat(passes(vanished.negated(), MINUTES)).isFalse();
        assertThat(conditions.cacheability(List.of(vanished)).contexts()).isEmpty();
    }

    @Test
    void theDecisionVariesByWhatEachConditionReads() {
        assertThat(conditions.cacheability(List.of(
                pages("/about"),
                ConditionConfig.of(UserRoleCondition.ID, Map.of()),
                ConditionConfig.of(LanguageCondition.ID, Map.of()),
                ConditionConfig.of(BLOCK_BUNDLES, Map.of()))).contexts())
                .containsExactly("url.path", UserRoleCondition.USER_ROLES_CONTEXT, "languages",
                        EntityBundleCondition.ROUTE_CONTEXT);
    }

    @Test
    void aPathConditionReadsItsPagesFromASubmissionDroppingBlankLines() {
        VisibilityCondition condition = conditions.condition(RequestPathCondition.ID);

        assertThat(condition.settingsValues("p_", Map.of("p_pages", " /about \n\n/minutes/*\n")))
                .contains(Map.of(RequestPathCondition.PAGES, "/about\n/minutes/*"));
        assertThat(condition.settingsValues("p_", Map.of("p_pages", "  \n"))).isEmpty();
        assertThat(condition.settingsValues("p_", Map.of())).isEmpty();
    }

    @Test
    void aPathConditionIsEditedAsOneListOfPages() {
        List<FormElement> form = conditions.condition(RequestPathCondition.ID)
                .settingsForm("p_", Map.of(RequestPathCondition.PAGES, "/about"));

        assertThat(form).extracting(FormElement::name).containsExactly("p_pages");
        assertThat(form.getFirst().value()).isEqualTo("/about");
    }

    @Test
    void aRoleConditionHoldsForSomeoneHoldingAChosenRole() {
        ConditionConfig editors = ConditionConfig.of(
                UserRoleCondition.ID, Map.of(UserRoleCondition.ROLES, List.of(EDITOR)));

        signedInHolding(EDITOR);
        assertThat(passes(editors, MINUTES)).isTrue();

        signedInHolding();
        assertThat(passes(editors, MINUTES)).isFalse();
    }

    @Test
    void aRoleConditionCanChooseVisitorsWhoHaveNotSignedIn() {
        ConditionConfig visitors = ConditionConfig.of(
                UserRoleCondition.ID, Map.of(UserRoleCondition.ROLES, List.of(RoleConfig.ANONYMOUS)));

        assertThat(passes(visitors, MINUTES)).isTrue();
        signedInHolding(EDITOR);
        assertThat(passes(visitors, MINUTES)).isFalse();
    }

    @Test
    void aRoleConditionOffersOneBoxPerRoleTickedForTheChosenOnes() {
        List<FormElement> form = conditions.condition(UserRoleCondition.ID)
                .settingsForm("r_", Map.of(UserRoleCondition.ROLES, List.of(EDITOR)));

        assertThat(form).extracting(FormElement::name).contains("r_anonymous", "r_authenticated", "r_editor");
        assertThat(form).filteredOn(element -> element.name().equals("r_editor"))
                .extracting(FormElement::value).containsExactly(true);
        assertThat(form).filteredOn(element -> element.name().equals("r_anonymous"))
                .extracting(FormElement::value).containsExactly(false);
    }

    @Test
    void aRoleConditionReadsTheTickedRolesFromASubmission() {
        VisibilityCondition condition = conditions.condition(UserRoleCondition.ID);

        assertThat(condition.settingsValues("r_", Map.of("r_editor", "on", "r_ghost", "on")))
                .contains(Map.of(UserRoleCondition.ROLES, List.of(EDITOR)));
        assertThat(condition.settingsValues("r_", Map.of())).isEmpty();
    }

    @Test
    void aBundleConditionHoldsOnAPageAboutAnEntityOfAChosenBundle() {
        ConditionConfig notices = ConditionConfig.of(
                BLOCK_BUNDLES, Map.of(EntityBundleCondition.BUNDLES, List.of("notice")));

        assertThat(passes(notices, MINUTES.withRouteEntity(
                EntityData.of(BlockContentEntityType.ID, 1L, "notice", "Closed", Map.of())))).isTrue();
        assertThat(passes(notices, MINUTES.withRouteEntity(
                EntityData.of(BlockContentEntityType.ID, 2L, "promotion", "Fair", Map.of())))).isFalse();
    }

    @Test
    void aBundleConditionDoesNotHoldOnAPageAboutNoEntityOrAnotherType() {
        ConditionConfig notices = ConditionConfig.of(
                BLOCK_BUNDLES, Map.of(EntityBundleCondition.BUNDLES, List.of("notice")));

        assertThat(passes(notices, MINUTES)).isFalse();
        assertThat(passes(notices, MINUTES.withRouteEntity(
                EntityData.of("user", 1L, "notice", "edith", Map.of())))).isFalse();
    }

    @Test
    void aBundleConditionIsNamedForItsEntityTypeAndOffersOneBoxPerBundle() {
        VisibilityCondition condition = conditions.condition(BLOCK_BUNDLES);

        List<FormElement> form = condition.settingsForm(
                "b_", Map.of(EntityBundleCondition.BUNDLES, List.of("notice")));

        assertThat(condition.label()).isEqualTo("Type of " + BlockContentEntityType.ID);
        assertThat(form).extracting(FormElement::name).containsExactly("b_notice", "b_promotion");
        assertThat(form).extracting(FormElement::value).containsExactly(true, false);
    }

    @Test
    void aBundleConditionReadsTheTickedBundlesFromASubmission() {
        VisibilityCondition condition = conditions.condition(BLOCK_BUNDLES);

        assertThat(condition.settingsValues("b_", Map.of("b_promotion", "on")))
                .contains(Map.of(EntityBundleCondition.BUNDLES, List.of("promotion")));
        assertThat(condition.settingsValues("b_", Map.of())).isEmpty();
    }

    @Test
    void aLanguageConditionHoldsForTheLanguageThePageIsShownIn() {
        ConditionConfig french = ConditionConfig.of(
                LanguageCondition.ID, Map.of(LanguageCondition.LANGCODES, List.of("fr")));

        LocaleContextHolder.setLocale(Locale.CANADA_FRENCH);
        assertThat(passes(french, MINUTES)).isTrue();

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(passes(french, MINUTES)).isFalse();
    }

    @Test
    void aLanguageConditionCanNameARegionalVariant() {
        ConditionConfig brazilian = ConditionConfig.of(
                LanguageCondition.ID, Map.of(LanguageCondition.LANGCODES, List.of("pt-br")));

        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-BR"));
        assertThat(passes(brazilian, MINUTES)).isTrue();

        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-PT"));
        assertThat(passes(brazilian, MINUTES)).isFalse();
    }

    @Test
    void aLanguageConditionReadsItsCodesFromACommaSeparatedList() {
        VisibilityCondition condition = conditions.condition(LanguageCondition.ID);

        assertThat(condition.settingsValues("l_", Map.of("l_langcodes", " EN, fr ,, pt-BR")))
                .contains(Map.of(LanguageCondition.LANGCODES, List.of("en", "fr", "pt-br")));
        assertThat(condition.settingsValues("l_", Map.of("l_langcodes", " , "))).isEmpty();
    }

    @Test
    void aLanguageConditionIsEditedAsTheListItReads() {
        List<FormElement> form = conditions.condition(LanguageCondition.ID)
                .settingsForm("l_", Map.of(LanguageCondition.LANGCODES, List.of("en", "fr")));

        assertThat(form.getFirst().value()).isEqualTo("en, fr");
        assertThat(form.getFirst().rules().getFirst().check("en, fr, pt-br")).isEmpty();
        assertThat(form.getFirst().rules().getFirst().check("english")).isPresent();
    }
}
