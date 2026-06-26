package dev.springdrop.kernel.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class KernelWebIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void healthEndpointIsServed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/no-such-page"))
                .andExpect(status().isNotFound());
    }
}
