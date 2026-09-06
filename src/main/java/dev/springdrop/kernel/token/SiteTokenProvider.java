package dev.springdrop.kernel.token;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.site.SiteInformation;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code [site:...]} tokens from the stored site information, so mail
 * and messages carry the site's current name, slogan, address, and URL.
 */
@Component
public class SiteTokenProvider implements TokenProvider {

    private final ConfigStore configStore;

    public SiteTokenProvider(ConfigStore configStore) {
        this.configStore = configStore;
    }

    @Override
    public String type() {
        return "site";
    }

    @Override
    public String resolve(String name, TokenContext context) {
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
        return switch (name) {
            case "name" -> site.name();
            case "slogan" -> site.slogan();
            case "mail" -> site.mail();
            case "url" -> site.url();
            default -> null;
        };
    }

    @Override
    public List<TokenDefinition> availableTokens() {
        return List.of(
                new TokenDefinition("name", "The name of the site"),
                new TokenDefinition("slogan", "The site slogan"),
                new TokenDefinition("mail", "The address site mail is sent from"),
                new TokenDefinition("url", "The absolute URL of the site"));
    }
}
