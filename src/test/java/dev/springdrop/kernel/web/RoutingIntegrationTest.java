package dev.springdrop.kernel.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.routing.EntityUpcaster;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.routing.ThemeResolver;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootTest
@AutoConfigureMockMvc
class RoutingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void frontEndRouteResolvesFrontEndTheme() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("theme", ThemeResolver.FRONT_END));
    }

    @Test
    void adminRouteResolvesAdminTheme() throws Exception {
        mockMvc.perform(get("/admin/things"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("theme", ThemeResolver.ADMIN));
    }

    @Test
    void upcastsAPathValueIntoTheEntity() throws Exception {
        mockMvc.perform(get("/widget/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Gadget"));
    }

    @Test
    void missingEntityYieldsNotFound() throws Exception {
        mockMvc.perform(get("/widget/999"))
                .andExpect(status().isNotFound());
    }

    record Widget(String id, String name) {
    }

    @TestConfiguration
    static class RoutingTestConfig {

        @Bean
        RouteRegistrar testRoutes() {
            return () -> List.of(RouteDefinition.admin("/admin/things", "things", "Things", null));
        }

        @Bean
        EntityUpcaster<Widget> widgetUpcaster() {
            return new EntityUpcaster<>() {
                @Override
                public Class<Widget> entityType() {
                    return Widget.class;
                }

                @Override
                public Optional<Widget> load(String id) {
                    return "1".equals(id) ? Optional.of(new Widget("1", "Gadget")) : Optional.empty();
                }
            };
        }

        @Controller
        static class AdminThingsController {
            @GetMapping("/admin/things")
            String things(Model model) {
                model.addAttribute("siteName", "Admin");
                return "home";
            }
        }

        @Controller
        static class WidgetController {
            @GetMapping("/widget/{widget}")
            @ResponseBody
            String show(Widget widget) {
                return widget.name();
            }
        }
    }
}
