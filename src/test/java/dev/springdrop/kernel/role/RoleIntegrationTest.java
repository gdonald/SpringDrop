package dev.springdrop.kernel.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionProvider;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.user.AccountDetailsService;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import dev.springdrop.web.RolePermissionsController;

@SpringBootTest
@AutoConfigureMockMvc
class RoleIntegrationTest extends AbstractIntegrationTest {

    static final String CATALOGUE = "read the catalogue";

    private static final String EDITOR = "editor";

    private static final String SECRET = "correct horse battery staple";

    @Autowired
    private RoleManager roles;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private AccountDetailsService accountDetails;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void theRolesEverySiteHas() {
        accounts.install();
        roles.install();
        roles.delete(EDITOR);
        roles.delete(RoleConfig.ADMINISTRATOR);
        roles.save(RoleConfig.of(RoleConfig.AUTHENTICATED, "Signed-in visitor", 1));
        accounts.findByName("edith").ifPresent(account -> entities.delete("user", account.id()));
    }

    @AfterEach
    void plainRolesAgain() {
        roles.delete(EDITOR);
        roles.delete(RoleConfig.ADMINISTRATOR);
        roles.save(RoleConfig.of(RoleConfig.AUTHENTICATED, "Signed-in visitor", 1));
    }

    private UserAccount accountHolding(String... roleIds) {
        UserAccount account = accounts.create("edith", "edith@example.com",
                passwordEncoder.encode(SECRET));
        entities.load(UserEntityType.ID, account.id()).ifPresent(stored -> {
            Map<String, Object> values = new LinkedHashMap<>(stored.fields());
            values.put(UserEntityType.ROLES, List.of(roleIds));
            entities.save(stored.withFields(values));
        });
        return account;
    }

    @Test
    void everySiteHasAnAnonymousAndAnAuthenticatedRole() {
        assertThat(roles.find(RoleConfig.ANONYMOUS))
                .hasValueSatisfying(role -> assertThat(role.label()).isEqualTo("Anonymous visitor"));
        assertThat(roles.find(RoleConfig.AUTHENTICATED)).isPresent();
    }

    @Test
    void installingTwiceLeavesTheRolesAsTheyWere() {
        roles.grant(RoleConfig.AUTHENTICATED, CATALOGUE);

        roles.install();

        assertThat(roles.find(RoleConfig.AUTHENTICATED))
                .hasValueSatisfying(role -> assertThat(role.grants(CATALOGUE)).isTrue());
    }

    @Test
    void rolesAreListedInTheOrderTheSiteKeepsThem() {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5));

        assertThat(roles.all()).extracting(role -> role.id())
                .containsExactly(RoleConfig.ANONYMOUS, RoleConfig.AUTHENTICATED, EDITOR);
    }

    @Test
    void grantingAPermissionToARoleGivesItToWhoeverHoldsTheRole() {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5));

        roles.grant(EDITOR, CATALOGUE);

        assertThat(roles.permissionsOf(List.of(EDITOR))).containsExactly(CATALOGUE);
        assertThat(roles.find(EDITOR)).hasValueSatisfying(role ->
                assertThat(role.grants(CATALOGUE)).isTrue());
    }

    @Test
    void grantingTheSamePermissionTwiceGrantsItOnce() {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5));

        roles.grant(EDITOR, CATALOGUE);
        roles.grant(EDITOR, CATALOGUE);

        assertThat(roles.find(EDITOR))
                .hasValueSatisfying(role -> assertThat(role.permissions()).containsExactly(CATALOGUE));
    }

    @Test
    void takingAPermissionBackLeavesTheRoleWithoutIt() {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5).granting(CATALOGUE));

        roles.revoke(EDITOR, CATALOGUE);

        assertThat(roles.permissionsOf(List.of(EDITOR))).isEmpty();
    }

    @Test
    void theAdministratorRoleCarriesEveryPermissionTheSiteHas() {
        roles.save(RoleConfig.of(RoleConfig.ADMINISTRATOR, "Administrator", 10).asAdministrator());

        assertThat(roles.permissionsOf(List.of(RoleConfig.ADMINISTRATOR)))
                .contains(Permissions.ADMINISTER_FIELDS, Permissions.ADMINISTER_PERMISSIONS, CATALOGUE);
        assertThat(roles.find(RoleConfig.ADMINISTRATOR))
                .hasValueSatisfying(role -> assertThat(role.grants("anything at all")).isTrue());
    }

    @Test
    void aRoleNobodyDefinedCarriesNothing() {
        assertThat(roles.permissionsOf(List.of("nonesuch"))).isEmpty();
    }

    @Test
    void signingInCarriesThePermissionsOfTheRolesTheAccountHolds() {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5).granting(CATALOGUE));
        accountHolding(EDITOR);

        assertThat(accountDetails.loadUserByUsername("edith").getAuthorities())
                .extracting(value -> value.toString())
                .containsExactly(CATALOGUE);
    }

    @Test
    void anAccountWithNoRolesOfItsOwnStillHasWhatEveryoneSignedInHas() {
        roles.grant(RoleConfig.AUTHENTICATED, CATALOGUE);
        accounts.create("edith", "edith@example.com", passwordEncoder.encode(SECRET));

        assertThat(accountDetails.loadUserByUsername("edith").getAuthorities())
                .extracting(value -> value.toString())
                .containsExactly(CATALOGUE);
    }

    @Test
    void aPermissionGrantedInTheMatrixGatesTheRouteAtOnce() throws Exception {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5));
        accountHolding(EDITOR);

        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("edith").password(SECRET));
        mockMvc.perform(get("/catalogue").with(user("edith"))).andExpect(status().isForbidden());

        mockMvc.perform(post(RolePermissionsController.PATH)
                        .param(EDITOR + RolePermissionsController.SEPARATOR + CATALOGUE, "on")
                        .with(administrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(accountDetails.loadUserByUsername("edith").getAuthorities())
                .extracting(value -> value.toString()).contains(CATALOGUE);
    }

    @Test
    void theMatrixShowsARowPerPermissionAndAColumnPerRole() throws Exception {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5).granting(CATALOGUE));

        Document document = Jsoup.parse(mockMvc.perform(get(RolePermissionsController.PATH)
                        .with(administrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.select("thead th")).extracting(element -> element.text())
                .containsExactly("Permission", "Anonymous visitor", "Signed-in visitor", "Editor");
        assertThat(document.selectFirst("input[name=" + EDITOR + "|" + CATALOGUE + "]").hasAttr("checked"))
                .isTrue();
    }

    @Test
    void theMatrixMarksThePermissionsToBeCarefulWith() throws Exception {
        Document document = Jsoup.parse(mockMvc.perform(get(RolePermissionsController.PATH)
                        .with(administrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.select(".badge")).extracting(element -> element.text())
                .contains("Trusted roles only");
    }

    @Test
    void theAdministratorRoleIsTickedThroughoutAndCannotBeChanged() throws Exception {
        roles.save(RoleConfig.of(RoleConfig.ADMINISTRATOR, "Administrator", 10).asAdministrator());

        Document document = Jsoup.parse(mockMvc.perform(get(RolePermissionsController.PATH)
                        .with(administrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.select("input[name^=administrator|]")).allSatisfy(box -> {
            assertThat(box.hasAttr("checked")).isTrue();
            assertThat(box.hasAttr("disabled")).isTrue();
        });
    }

    @Test
    void aBoxLeftUntickedInTheMatrixTakesThePermissionBack() throws Exception {
        roles.save(RoleConfig.of(EDITOR, "Editor", 5).granting(CATALOGUE));

        mockMvc.perform(post(RolePermissionsController.PATH)
                        .with(administrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(roles.permissionsOf(List.of(EDITOR))).isEmpty();
    }

    @Test
    void theMatrixIsClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(RolePermissionsController.PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor administrator() {
        return user("admin").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_PERMISSIONS));
    }

    @TestConfiguration
    static class CataloguePermission {

        @Bean
        PermissionProvider cataloguePermissions() {
            return () -> List.of(
                    PermissionDefinition.of(CATALOGUE, "Read the catalogue", "catalogue"));
        }

        @Bean
        RouteRegistrar catalogueRoutes() {
            return () -> List.of(
                    RouteDefinition.admin("/catalogue", "catalogue", "Catalogue", CATALOGUE));
        }

        @Controller
        static class CatalogueController {

            @GetMapping("/catalogue")
            @ResponseBody
            String catalogue() {
                return "the catalogue";
            }
        }
    }
}
