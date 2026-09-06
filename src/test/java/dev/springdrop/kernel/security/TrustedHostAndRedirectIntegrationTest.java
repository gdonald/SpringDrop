package dev.springdrop.kernel.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "springdrop.security.trusted-host.patterns=^springdrop\\.example$,^localhost$",
        "springdrop.security.redirect.allowed-hosts=partner.example"
})
@AutoConfigureMockMvc
class TrustedHostAndRedirectIntegrationTest extends AbstractIntegrationTest {

    private static final String NAME = "hostess";

    private static final String SECRET = "correct horse battery staple";

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void anAccountToSignInWith() {
        accounts.install();
        accounts.findByName(NAME).ifPresent(account -> entities.delete("user", account.id()));
        accounts.create(NAME, "hostess@example.com", passwordEncoder.encode(SECRET));
    }

    @Test
    void aRequestNamingAHostTheSiteDoesNotAnswerToIsRefused() throws Exception {
        mockMvc.perform(get("http://elsewhere.example/"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(TrustedHostFilter.MESSAGE));
    }

    @Test
    void aRequestNamingATrustedHostIsAnswered() throws Exception {
        mockMvc.perform(get("http://springdrop.example/"))
                .andExpect(status().isOk());
    }

    @Test
    void signingInReturnsToTheDestinationAskedFor() throws Exception {
        mockMvc.perform(signIn("/admin/people"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/admin/people"));
    }

    @Test
    void aDestinationLeadingOffSiteIsRefusedAndTheFrontPageUsedInstead() throws Exception {
        mockMvc.perform(signIn("https://elsewhere.example/landing"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void aDestinationOnAnAllowlistedHostIsKept() throws Exception {
        mockMvc.perform(signIn("https://partner.example/welcome"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("https://partner.example/welcome"));
    }

    @Test
    void theSignInFormCarriesTheDestinationItWasAskedFor() throws Exception {
        mockMvc.perform(get(SecurityConfig.LOGIN_PATH)
                        .param(DestinationSuccessHandler.DESTINATION, "/admin/people"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"destination\" value=\"/admin/people\"")));
    }

    private static MockHttpServletRequestBuilder signIn(String destination) {
        return post(SecurityConfig.LOGIN_PATH)
                .param("username", NAME)
                .param("password", SECRET)
                .param(DestinationSuccessHandler.DESTINATION, destination)
                .with(csrf());
    }
}
