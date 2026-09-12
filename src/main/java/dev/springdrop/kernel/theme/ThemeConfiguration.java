package dev.springdrop.kernel.theme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The themes core ships. A module contributes its own by declaring a {@link Theme} bean. */
@Configuration
public class ThemeConfiguration {

    @Bean
    Theme frontEndTheme() {
        return Theme.named(Theme.FRONT_END);
    }

    /**
     * The admin theme inherits the base theme, so it overrides the layout and
     * leaves the shared partials alone.
     */
    @Bean
    Theme adminTheme() {
        return Theme.extending(Theme.ADMIN, Theme.FRONT_END);
    }
}
