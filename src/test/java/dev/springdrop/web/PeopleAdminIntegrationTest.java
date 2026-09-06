package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountCancellationService;
import dev.springdrop.kernel.user.CancellationMethod;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class PeopleAdminIntegrationTest extends AbstractIntegrationTest {

    record Article(long id, String label) {
    }

    /** Content that records who wrote it, so cancellation has something to act on. */
    static final EntityType ARTICLE = EntityType.content("owned_article", Article.class)
            .withBaseFields(List.of(
                    BaseFieldDefinition.optional(BaseFieldDefinition.OWNER, ColumnType.BIGINT),
                    BaseFieldDefinition.optional(BaseFieldDefinition.STATUS, ColumnType.BOOLEAN)));

    private static final String EDITOR = "editor";

    private static final String SECRET = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private AccountCancellationService cancellations;

    @Autowired
    private RoleManager roles;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void twoAccountsAndSomeContent() {
        accounts.install();
        roles.install();
        roles.save(RoleConfig.of(EDITOR, "Editor", 5));
        entityTypeManager.installStorage("owned_article");

        List.of("edith", "wilma").forEach(name ->
                accounts.findByName(name).ifPresent(account -> entities.delete("user", account.id())));
        List.of(1L, 2L).forEach(id -> entities.delete("owned_article", id));
    }

    @AfterEach
    void plainRolesAgain() {
        roles.delete(EDITOR);
    }

    private UserAccount account(String name) {
        return accounts.create(name, name + "@example.com", passwordEncoder.encode(SECRET));
    }

    private void articleOwnedBy(long id, long owner) {
        entities.save(new EntityData("owned_article", id, null, null, "An article",
                EntityData.DEFAULT_LANGCODE, null,
                Map.of(BaseFieldDefinition.OWNER, owner, BaseFieldDefinition.STATUS, true)));
    }

    private RequestPostProcessor peopleAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_USERS));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(peopleAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void thePeopleListIsClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(PeopleController.PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void thePeopleListShowsEachAccountWithEditAndCloseAsButtons() throws Exception {
        account("edith");

        Document document = page(PeopleController.PATH);

        assertThat(document.select("tbody tr")).isNotEmpty();
        assertThat(document.text()).contains("edith");
        BootstrapAssertions.assertEditControlsAreButtons(document);
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void theListCanBeNarrowedByName() throws Exception {
        account("edith");
        account("wilma");

        Document document = page(PeopleController.PATH + "?name=edi");

        assertThat(document.select("tbody tr")).hasSize(1);
        assertThat(document.selectFirst("tbody td").text()).isEqualTo("edith");
    }

    @Test
    void theListCanBeNarrowedByStatus() throws Exception {
        UserAccount blocked = account("edith");
        account("wilma");
        accounts.block(blocked.id());

        assertThat(page(PeopleController.PATH + "?status=false").text()).contains("edith")
                .doesNotContain("wilma");
        assertThat(page(PeopleController.PATH + "?status=true").text()).contains("wilma");
    }

    @Test
    void theListCanBeNarrowedByRole() throws Exception {
        UserAccount edith = account("edith");
        account("wilma");
        giveRole(edith.id(), EDITOR);

        Document document = page(PeopleController.PATH + "?role=" + EDITOR);

        assertThat(document.select("tbody tr")).hasSize(1);
        assertThat(document.selectFirst("tbody td").text()).isEqualTo("edith");
    }

    @Test
    void theEditFormOffersTheRolesASiteHasDefined() throws Exception {
        UserAccount edith = account("edith");

        Document document = page(PeopleController.PATH + "/" + edith.id() + "/edit");

        assertThat(document.selectFirst("input[name=role_editor]")).isNotNull();
        assertThat(document.select("input[name=role_anonymous]")).isEmpty();
        assertThat(document.select("input[name=role_authenticated]")).isEmpty();
    }

    @Test
    void theEditFormTicksTheRolesTheAccountAlreadyHolds() throws Exception {
        UserAccount edith = account("edith");
        giveRole(edith.id(), EDITOR);

        Document document = page(PeopleController.PATH + "/" + edith.id() + "/edit");

        assertThat(document.selectFirst("input[name=role_editor]").hasAttr("checked")).isTrue();
    }

    @Test
    void changingRolesOnTheEditFormSticks() throws Exception {
        UserAccount edith = account("edith");

        mockMvc.perform(post(PeopleController.PATH + "/" + edith.id() + "/edit")
                        .param("role_" + EDITOR, "on")
                        .with(peopleAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(rolesOf(edith.id())).containsExactly(EDITOR);

        mockMvc.perform(post(PeopleController.PATH + "/" + edith.id() + "/edit")
                        .with(peopleAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(rolesOf(edith.id())).isEmpty();
    }

    @Test
    void closingAnAccountAsksFirstAndOffersEachMethod() throws Exception {
        UserAccount edith = account("edith");

        Document document = page(PeopleController.PATH + "/" + edith.id() + "/cancel");

        assertThat(document.selectFirst("h1").text()).contains("Close the account edith?");
        assertThat(document.select("select[name=method] option"))
                .extracting(option -> option.attr("value"))
                .containsExactly("BLOCK", "BLOCK_AND_UNPUBLISH", "REASSIGN_TO_ANONYMOUS", "DELETE");
        assertThat(accounts.find(edith.id())).isPresent();
    }

    @Test
    void blockingClosesTheAccountAndLeavesWhatItWroteAlone() {
        UserAccount edith = account("edith");
        articleOwnedBy(1L, edith.id());

        cancellations.cancel(edith.id(), CancellationMethod.BLOCK);

        assertThat(accounts.find(edith.id()))
                .hasValueSatisfying(account -> assertThat(account.active()).isFalse());
        assertThat(entities.load("owned_article", 1L)).hasValueSatisfying(article ->
                assertThat(article.fields()).containsEntry(BaseFieldDefinition.STATUS, true));
    }

    @Test
    void blockingAndUnpublishingTakesWhatItWroteOutOfSight() {
        UserAccount edith = account("edith");
        articleOwnedBy(1L, edith.id());

        cancellations.cancel(edith.id(), CancellationMethod.BLOCK_AND_UNPUBLISH);

        assertThat(accounts.find(edith.id()))
                .hasValueSatisfying(account -> assertThat(account.active()).isFalse());
        assertThat(entities.load("owned_article", 1L)).hasValueSatisfying(article ->
                assertThat(article.fields()).containsEntry(BaseFieldDefinition.STATUS, false));
    }

    @Test
    void reassigningKeepsWhatItWroteAndCreditsNobody() {
        UserAccount edith = account("edith");
        articleOwnedBy(1L, edith.id());

        cancellations.cancel(edith.id(), CancellationMethod.REASSIGN_TO_ANONYMOUS);

        assertThat(accounts.find(edith.id())).isEmpty();
        assertThat(entities.load("owned_article", 1L)).hasValueSatisfying(article ->
                assertThat(((Number) article.fields().get(BaseFieldDefinition.OWNER)).longValue())
                        .isEqualTo(UserAccount.ANONYMOUS_ID));
    }

    @Test
    void deletingTakesTheAccountAndWhatItWroteWithIt() {
        UserAccount edith = account("edith");
        articleOwnedBy(1L, edith.id());

        cancellations.cancel(edith.id(), CancellationMethod.DELETE);

        assertThat(accounts.find(edith.id())).isEmpty();
        assertThat(entities.load("owned_article", 1L)).isEmpty();
    }

    @Test
    void closingOneAccountLeavesAnotherPersonsContentAlone() {
        UserAccount edith = account("edith");
        UserAccount wilma = account("wilma");
        articleOwnedBy(1L, edith.id());
        articleOwnedBy(2L, wilma.id());

        cancellations.cancel(edith.id(), CancellationMethod.DELETE);

        assertThat(entities.load("owned_article", 2L)).isPresent();
    }

    @Test
    void closingThroughThePageCarriesOutTheChosenMethod() throws Exception {
        UserAccount edith = account("edith");
        articleOwnedBy(1L, edith.id());

        mockMvc.perform(post(PeopleController.PATH + "/" + edith.id() + "/cancel")
                        .param(PeopleController.METHOD, CancellationMethod.BLOCK_AND_UNPUBLISH.name())
                        .with(peopleAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(entities.load("owned_article", 1L)).hasValueSatisfying(article ->
                assertThat(article.fields()).containsEntry(BaseFieldDefinition.STATUS, false));
    }

    @Test
    void everyMethodSaysWhatItDoes() {
        assertThat(cancellations.methods()).extracting(method -> method.label())
                .allSatisfy(label -> assertThat(label).isNotBlank());
    }

    private void giveRole(long accountId, String role) {
        entities.load(UserEntityType.ID, accountId).ifPresent(account -> {
            Map<String, Object> values = new java.util.LinkedHashMap<>(account.fields());
            values.put(UserEntityType.ROLES, List.of(role));
            entities.save(account.withFields(values));
        });
    }

    private List<Object> rolesOf(long accountId) {
        Object held = entities.load(UserEntityType.ID, accountId)
                .map(account -> account.fields().get(UserEntityType.ROLES))
                .orElse(List.of());
        return (held instanceof List<?> names) ? List.copyOf(names) : List.of();
    }

    @TestConfiguration
    static class OwnedContent {

        @Bean
        EntityTypeProvider ownedArticleType() {
            return () -> List.of(ARTICLE);
        }
    }
}
