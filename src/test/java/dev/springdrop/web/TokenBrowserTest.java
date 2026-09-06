package dev.springdrop.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class TokenBrowserTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    @Test
    void listsTheTokensOfEveryRegisteredProvider() throws Exception {
        mockMvc.perform(get(TokenBrowserController.PATH).with(administrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("[site:name]"),
                        Matchers.containsString("[current-user:name]"),
                        Matchers.containsString("[date:short]"),
                        Matchers.containsString("[node:author:name]"),
                        Matchers.containsString("The name of the site"))));
    }

    @Test
    void isReachableOnlyByAnAdministrator() throws Exception {
        mockMvc.perform(get(TokenBrowserController.PATH))
                .andExpect(status().isForbidden());
    }
}
