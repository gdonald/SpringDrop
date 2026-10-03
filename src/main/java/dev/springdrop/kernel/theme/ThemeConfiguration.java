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

    /** The widths the base theme's Bootstrap grid changes at. */
    @Bean
    BreakpointGroup frontEndBreakpoints() {
        List<String> densities = List.of("1x", "2x");
        return new BreakpointGroup(Theme.FRONT_END, "Front end", List.of(
                new Breakpoint(Theme.FRONT_END + ".xs", "Extra small", Breakpoint.ALL, 0, densities),
                new Breakpoint(Theme.FRONT_END + ".sm", "Small", "(min-width: 576px)", 1, densities),
                new Breakpoint(Theme.FRONT_END + ".md", "Medium", "(min-width: 768px)", 2, densities),
                new Breakpoint(Theme.FRONT_END + ".lg", "Large", "(min-width: 992px)", 3, densities),
                new Breakpoint(Theme.FRONT_END + ".xl", "Extra large", "(min-width: 1200px)", 4, densities),
                new Breakpoint(Theme.FRONT_END + ".xxl", "Extra extra large", "(min-width: 1400px)", 5, densities)));
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
