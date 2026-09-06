package dev.springdrop.kernel.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.web.CorrelationIdFilter;
import dev.springdrop.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The production profile, with the datasource credentials the profile requires
 * from the environment supplied as properties. The Testcontainers connection
 * details take precedence over them, so the context talks to the test database.
 */
@SpringBootTest(properties = {
        "SPRINGDROP_DB_URL=jdbc:postgresql://localhost:5432/springdrop",
        "SPRINGDROP_DB_USERNAME=springdrop",
        "SPRINGDROP_DB_PASSWORD=springdrop",
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ActuatorAccessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousIsDeniedTheHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsDeniedTheInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorReachesTheHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health").with(administrator()))
                .andExpect(status().isOk());
    }

    @Test
    void anAdministratorReachesTheInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info").with(administrator()))
                .andExpect(status().isOk());
    }

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor administrator() {
        return user("admin").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }
}
