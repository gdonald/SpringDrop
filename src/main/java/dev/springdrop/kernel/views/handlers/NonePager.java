package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.theme.Pager;
import dev.springdrop.kernel.views.PagerPlugin;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.Settings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/** Every result at once, after the {@code offset}, with no links between pages. */
@SpringDropPlugin(id = NonePager.ID, type = PagerPlugin.class)
public class NonePager implements PagerPlugin {

    public static final String ID = "none";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Display all items";
    }

    @Override
    public int itemsPerPage(PluginConfig config) {
        return 0;
    }

    @Override
    public boolean pages() {
        return false;
    }

    @Override
    public Optional<Pager> links(int page, int totalPages, IntFunction<String> url) {
        return Optional.empty();
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.number(prefix, OFFSET, "Items to skip", settings, 0));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(OFFSET, Settings.whole(prefix, OFFSET, submitted, 0));
    }
}
