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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.i18n.LocaleContextHolder;

/** Shows a block when the page is shown in one of the languages listed. */
@SpringDropPlugin(id = LanguageCondition.ID, type = VisibilityCondition.class)
public class LanguageCondition implements VisibilityCondition {

    public static final String ID = "language";

    public static final String LANGCODES = "langcodes";

    /** Language codes such as {@code en} or {@code pt-br}, separated by commas. */
    public static final String LANGCODE_LIST_PATTERN =
            "\\s*[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?(\\s*,\\s*[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?)*\\s*";

    @Override
    public String label() {
        return "Language";
    }

    @Override
    public boolean evaluate(Map<String, Object> settings, BlockContext context) {
        String current = LocaleContextHolder.getLocale().toLanguageTag().toLowerCase(Locale.ROOT);
        String language = LocaleContextHolder.getLocale().getLanguage();
        List<String> chosen = BlockSettings.strings(settings, LANGCODES);
        return chosen.contains(current) || chosen.contains(language);
    }

    @Override
    public CacheMetadata cacheability() {
        return CacheMetadata.EMPTY.withContext("languages");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(FormElement.of(ElementType.TEXTFIELD, prefix + LANGCODES)
                .label("Languages")
                .description("Language codes separated by commas, such as en, fr, pt-br.")
                .value(String.join(", ", BlockSettings.strings(settings, LANGCODES)))
                .rule(ValidationRule.pattern(LANGCODE_LIST_PATTERN)));
    }

    @Override
    public Optional<Map<String, Object>> settingsValues(String prefix, Map<String, String> submitted) {
        List<String> chosen = Arrays.stream(submitted.getOrDefault(prefix + LANGCODES, "").split(","))
                .map(code -> code.strip().toLowerCase(Locale.ROOT))
                .filter(code -> !code.isEmpty())
                .toList();
        return chosen.isEmpty() ? Optional.empty() : Optional.of(Map.of(LANGCODES, chosen));
    }
}
