package dev.springdrop.kernel.theme;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The themes core ships. A module contributes its own by declaring a {@link Theme} bean. */
@Configuration
public class ThemeConfiguration {

    @Bean
    Theme frontEndTheme() {
        return Theme.named(Theme.FRONT_END).withRegions(List.of(
                new Region("header", "Header"),
                new Region("primary_menu", "Primary menu"),
                new Region("breadcrumb", "Breadcrumb"),
                new Region("highlighted", "Highlighted"),
                new Region("help", "Help"),
                new Region(Region.CONTENT, "Content"),
                new Region("sidebar", "Sidebar"),
                new Region("footer", "Footer")));
    }

    /**
     * The admin theme inherits the base theme, so it overrides the layout and
     * leaves the shared partials alone.
     */
    @Bean
    Theme adminTheme() {
        return Theme.extending(Theme.ADMIN, Theme.FRONT_END).withRegions(List.of(
                new Region("header", "Header"),
                new Region("breadcrumb", "Breadcrumb"),
                new Region("highlighted", "Highlighted"),
                new Region("help", "Help"),
                new Region(Region.CONTENT, "Content")));
    }
}
