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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootTest
@AutoConfigureMockMvc
class AccessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousIsDeniedAPermissionedRoute() throws Exception {
        mockMvc.perform(get("/secure-area"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserWithThePermissionReachesThePermissionedRoute() throws Exception {
        mockMvc.perform(get("/secure-area")
                        .with(user("editor").authorities(new SimpleGrantedAuthority("manage secure"))))
                .andExpect(status().isOk())
                .andExpect(content().string("secret"));
    }

    @Test
    void aUserWithoutThePermissionIsDenied() throws Exception {
        mockMvc.perform(get("/secure-area")
                        .with(user("editor").authorities(new SimpleGrantedAuthority("something else"))))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class SecureRoutes {

        @Bean
        RouteRegistrar secureRoutes() {
            return () -> List.of(RouteDefinition.admin("/secure-area", "secure", "Secure", "manage secure"));
        }

        @Controller
        static class SecureController {
            @GetMapping("/secure-area")
            @ResponseBody
            String secure() {
                return "secret";
            }
        }
    }
}
