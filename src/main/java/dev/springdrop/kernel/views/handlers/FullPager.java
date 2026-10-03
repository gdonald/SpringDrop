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

/** Pages of results with a link to every page and to the previous and next ones. */
@SpringDropPlugin(id = FullPager.ID, type = PagerPlugin.class)
public class FullPager implements PagerPlugin {

    public static final String ID = "full";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Paged output, full pager";
    }

    @Override
    public boolean pages() {
        return true;
    }

    @Override
    public Optional<Pager> links(int page, int totalPages, IntFunction<String> url) {
        return totalPages > 1 ? Optional.of(Pager.of(page, totalPages, url)) : Optional.empty();
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
