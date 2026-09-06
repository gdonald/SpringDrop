package dev.springdrop.kernel.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionRegistryIntegrationTest extends AbstractIntegrationTest {

    /** A permission the test module declares in code rather than in a file. */
    static final String CURATE = "curate the collection";

    @Autowired
    private PermissionRegistry permissions;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aPermissionDeclaredInAFileIsInTheRegistry() {
        assertThat(permissions.find(Permissions.ADMINISTER_FIELDS)).hasValueSatisfying(permission -> {
            assertThat(permission.title()).isEqualTo("Administer fields");
            assertThat(permission.description()).contains("Add, change, and remove fields");
            assertThat(permission.restricted()).isTrue();
            assertThat(permission.provider()).isEqualTo("core");
        });
    }

    @Test
    void aPermissionDeclaredByAModuleIsInTheRegistryToo() {
        assertThat(permissions.has(CURATE)).isTrue();
        assertThat(permissions.find(CURATE))
                .hasValueSatisfying(permission -> assertThat(permission.provider()).isEqualTo("collection"));
    }

    @Test
    void aPermissionNobodyDeclaredIsNotInTheRegistry() {
        assertThat(permissions.has("do whatever you like")).isFalse();
        assertThat(permissions.find("do whatever you like")).isEmpty();
    }

    @Test
    void theRegistryGroupsPermissionsByTheModuleThatDeclaresThem() {
        assertThat(permissions.providedBy("core")).extracting(permission -> permission.name())
                .contains(Permissions.ADMINISTER_SITE_CONFIGURATION, Permissions.ADMINISTER_FIELDS);
        assertThat(permissions.providedBy("collection")).extracting(permission -> permission.name())
                .containsExactly(CURATE);
        assertThat(permissions.providedBy("nonesuch")).isEmpty();
    }

    @Test
    void theRegistryNamesThePermissionsToBeCarefulWith() {
        assertThat(permissions.restricted()).extracting(permission -> permission.name())
                .contains(Permissions.ADMINISTER_FIELDS)
                .doesNotContain(CURATE);
    }

    @Test
    void everyDeclaredPermissionIsListedOnce() {
        assertThat(permissions.all()).extracting(permission -> permission.name()).doesNotHaveDuplicates();
    }

    @Test
    void aModuleDeclaredPermissionGatesTheRouteThatNamesIt() throws Exception {
        mockMvc.perform(get("/collection").with(user("curator")
                        .authorities(new SimpleGrantedAuthority(CURATE))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/collection").with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPermissionCanBeBuiltUpInCode() {
        PermissionDefinition permission = PermissionDefinition.of("mind the shop", "Mind the shop", "shop")
                .describedAs("Serve behind the counter.")
                .asRestricted();

        assertThat(permission.description()).isEqualTo("Serve behind the counter.");
        assertThat(permission.restricted()).isTrue();
    }

    @TestConfiguration
    static class CollectionModule {

        @Bean
        PermissionProvider collectionPermissions() {
            return () -> List.of(PermissionDefinition.of(CURATE, "Curate the collection", "collection"));
        }

        @Bean
        RouteRegistrar collectionRoutes() {
            return () -> List.of(
                    RouteDefinition.admin("/collection", "collection", "The collection", CURATE));
        }

        @Controller
        static class CollectionController {

            @GetMapping("/collection")
            @ResponseBody
            String collection() {
                return "the collection";
            }
        }
    }
}
