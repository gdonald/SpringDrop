package dev.springdrop.kernel.maintenance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "springdrop.maintenance.enabled=true")
class MaintenanceModeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousVisitorSeesTheMaintenancePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Maintenance")));
    }

    @Test
    void healthCheckStaysReachableDuringMaintenance() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
