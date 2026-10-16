package dev.springdrop.web;

import dev.springdrop.kernel.block.PageHelp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives every view the help text for its page, as the {@code pageHelp} model
 * attribute, the same text the help block shows on a themed page.
 */
@ControllerAdvice
public class PageHelpAdvice {

    private final List<PageHelp> help;

    public PageHelpAdvice(List<PageHelp> help) {
        this.help = List.copyOf(help);
    }

    /** The help for one page, looked up when a template first asks for it. */
    public static final class View {

        private final List<PageHelp> help;
        private final String path;

        View(List<PageHelp> help, String path) {
            this.help = help;
            this.path = path;
        }

        public String text() {
            return help.stream().map(provider -> provider.helpFor(path)).flatMap(Optional::stream).findFirst()
                    .orElse("");
        }
    }

    @ModelAttribute("pageHelp")
    public View pageHelp(HttpServletRequest request) {
        return new View(help, request.getRequestURI());
    }
}
