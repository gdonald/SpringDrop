package dev.springdrop.web;

import dev.springdrop.kernel.token.TokenDefinition;
import dev.springdrop.kernel.token.TokenProvider;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Lists every token the registered providers offer, so an editor filling in a
 * mail template or a pattern can see what is available and copy the exact token.
 */
@Controller
public class TokenBrowserController {

    public static final String PATH = "/admin/help/tokens";

    /** One provider's tokens, grouped for the browser table. */
    public record TokenGroup(String type, List<TokenDefinition> tokens) {
    }

    private final List<TokenProvider> providers;

    public TokenBrowserController(List<TokenProvider> providers) {
        this.providers = providers;
    }

    @GetMapping(PATH)
    public String browse(Model model) {
        List<TokenGroup> groups = providers.stream()
                .map(provider -> new TokenGroup(provider.type(), provider.availableTokens()))
                .sorted(Comparator.comparing((TokenGroup group) -> group.type()))
                .toList();
        model.addAttribute("tokenGroups", groups);
        return "admin/token-browser";
    }
}
