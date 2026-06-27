package dev.springdrop.kernel.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootTest
@AutoConfigureMockMvc
class CsrfIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsAStateChangingRequestWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/submit"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAStateChangingRequestWithAValidCsrfToken() throws Exception {
        mockMvc.perform(post("/submit").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @TestConfiguration
    static class FormRoutes {

        @Controller
        static class FormController {
            @PostMapping("/submit")
            @ResponseBody
            String submit() {
                return "ok";
            }
        }
    }
}
