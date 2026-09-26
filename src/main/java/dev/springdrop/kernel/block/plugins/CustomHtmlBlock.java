package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.block.BlockSettings;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.Renderable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Markup written into the placement itself. It is cleaned against a list of
 * safe elements as it is drawn, so a script or an event handler written into it
 * never reaches the page.
 */
@SpringDropPlugin(id = CustomHtmlBlock.ID, type = BlockPlugin.class)
public class CustomHtmlBlock implements BlockPlugin {

    public static final String ID = "custom_html";

    public static final String BODY = "body";

    public static final String BODY_ELEMENT = SETTINGS_PREFIX + BODY;

    public static final int BODY_MAX_LENGTH = 65_535;

    @Override
    public String label() {
        return "Custom HTML";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        String body = BlockSettings.string(settings, BODY);
        if (body.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Renderable.of("markup").with("value", Jsoup.clean(body, Safelist.relaxed())));
    }

    @Override
    public List<FormElement> settingsForm(Map<String, Object> settings) {
        return List.of(FormElement.of(ElementType.TEXTAREA, BODY_ELEMENT)
                .label("Body")
                .description("HTML. Scripts, styles, and event handlers are removed when it is shown.")
                .markRequired()
                .value(BlockSettings.string(settings, BODY))
                .rule(ValidationRule.maxLength(BODY_MAX_LENGTH)));
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        return Map.of(BODY, submitted.getOrDefault(BODY_ELEMENT, ""));
    }
}
