package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.theme.Pager;
import dev.springdrop.kernel.views.PagerPlugin;
import dev.springdrop.kernel.views.Settings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/** A fixed number of results, after the {@code offset}, with no links to more. */
@SpringDropPlugin(id = SomePager.ID, type = PagerPlugin.class)
public class SomePager implements PagerPlugin {

    public static final String ID = "some";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Display a specified number of items";
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
        return List.of(Settings.number(prefix, ITEMS_PER_PAGE, "Items per page", settings, 10),
                Settings.number(prefix, OFFSET, "Items to skip", settings, 0));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(ITEMS_PER_PAGE, Settings.whole(prefix, ITEMS_PER_PAGE, submitted, 10),
                OFFSET, Settings.whole(prefix, OFFSET, submitted, 0));
    }
}
