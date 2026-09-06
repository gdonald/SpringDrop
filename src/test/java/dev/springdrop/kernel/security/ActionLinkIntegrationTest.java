package dev.springdrop.kernel.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootTest
@AutoConfigureMockMvc
class ActionLinkIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTION_PATH = "/admin/widgets/enable";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActionLinkTokenService actionLinkTokenService;

    private RequestPostProcessor operator() {
        return user("operator").authorities(new SimpleGrantedAuthority("manage widgets"));
    }

    @Test
    void aPermittedUserFollowingAnUntokenizedActionLinkIsDenied() throws Exception {
        mockMvc.perform(get(ACTION_PATH).with(operator()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aForgedTokenIsRejected() throws Exception {
        mockMvc.perform(get(ACTION_PATH)
                        .param(ActionLinkTokenService.TOKEN_PARAMETER, "forged")
                        .with(operator()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theTokenIssuedForTheUserLetsThemFollowTheActionLink() throws Exception {
        String token = actionLinkTokenService.token(ACTION_PATH, "operator");

        mockMvc.perform(get(ACTION_PATH)
                        .param(ActionLinkTokenService.TOKEN_PARAMETER, token)
                        .with(operator()))
                .andExpect(status().isOk())
                .andExpect(content().string("enabled"));
    }

    @Test
    void aTokenIssuedForAnotherUserIsRejected() throws Exception {
        String token = actionLinkTokenService.token(ACTION_PATH, "someone-else");

        mockMvc.perform(get(ACTION_PATH)
                        .param(ActionLinkTokenService.TOKEN_PARAMETER, token)
                        .with(operator()))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class ActionRoutes {

        @Bean
        RouteRegistrar actionLinkRoutes() {
            return () -> List.of(
                    RouteDefinition.adminAction(ACTION_PATH, "widget_enable", "Enable widget", "manage widgets"));
        }

        @Controller
        static class ActionController {

            @GetMapping(ACTION_PATH)
            @ResponseBody
            String enable() {
                return "enabled";
            }
        }
    }
}
