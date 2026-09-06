package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyResponseCarriesTheConfiguredHeaders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'self'")));
    }

    @Test
    void thePagesInlineScriptCarriesTheNonceThePolicyNames() throws Exception {
        MvcResult result = mockMvc.perform(get(SecurityConfig.LOGIN_PATH))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpServletResponse response = result.getResponse();
        String nonce = Jsoup.parse(response.getContentAsString())
                .selectFirst("script[type=module]").attr("nonce");

        assertThat(nonce).isNotEmpty();
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("'nonce-" + nonce + "'");
    }

    @Test
    void eachResponseIsGivenANonceOfItsOwn() throws Exception {
        String first = mockMvc.perform(get("/")).andReturn()
                .getResponse().getHeader("Content-Security-Policy");
        String second = mockMvc.perform(get("/")).andReturn()
                .getResponse().getHeader("Content-Security-Policy");

        assertThat(first).isNotEqualTo(second);
    }
}
