package dev.springdrop.kernel.block.conditions;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockSettings;
import dev.springdrop.kernel.block.VisibilityCondition;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shows a block on the pages listed, one path per line. An asterisk matches
 * anything, so {@code /trips/*} covers every trip, and {@code <front>} stands for
 * the front page.
 */
@SpringDropPlugin(id = RequestPathCondition.ID, type = VisibilityCondition.class)
public class RequestPathCondition implements VisibilityCondition {

    public static final String ID = "request_path";

    public static final String PAGES = "pages";

    public static final String FRONT_PAGE = "<front>";

    public static final int PAGES_MAX_LENGTH = 4096;

    @Override
    public String label() {
        return "Pages";
    }

    @Override
    public boolean evaluate(Map<String, Object> settings, BlockContext context) {
        return patterns(BlockSettings.string(settings, PAGES)).stream()
                .anyMatch(pattern -> matches(pattern, context.path()));
    }

    @Override
    public CacheMetadata cacheability() {
        return CacheMetadata.EMPTY.withContext("url.path");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(FormElement.of(ElementType.TEXTAREA, prefix + PAGES)
                .label("Pages")
                .description("One path per line. * matches anything, and <front> is the front page.")
                .value(BlockSettings.string(settings, PAGES))
                .rule(ValidationRule.maxLength(PAGES_MAX_LENGTH)));
    }

    @Override
    public Optional<Map<String, Object>> settingsValues(String prefix, Map<String, String> submitted) {
        List<String> patterns = patterns(submitted.getOrDefault(prefix + PAGES, ""));
        return patterns.isEmpty()
                ? Optional.empty()
                : Optional.of(Map.of(PAGES, String.join("\n", patterns)));
    }

    private static List<String> patterns(String pages) {
        return pages.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    static boolean matches(String pattern, String path) {
        String target = FRONT_PAGE.equals(pattern) ? "/" : pattern;
        String regex = Arrays.stream(target.split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));
        return path.matches(regex);
    }
}
