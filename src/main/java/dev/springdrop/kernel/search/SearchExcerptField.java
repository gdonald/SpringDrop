package dev.springdrop.kernel.search;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The excerpt of a result's document for the keywords the reader gave under
 * the {@code identifier} setting, {@code keys} by default, with the matched
 * words marked. A result the keywords did not find shows nothing.
 */
@SpringDropPlugin(id = SearchExcerptField.ID, type = FieldHandler.class)
public class SearchExcerptField implements FieldHandler {

    public static final String ID = "search_excerpt";

    public static final String IDENTIFIER = "identifier";

    public static final String DEFAULT_IDENTIFIER = "keys";

    private final SearchService search;

    public SearchExcerptField(SearchService search) {
        this.search = search;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Search excerpt";
    }

    @Override
    public boolean sortable() {
        return false;
    }

    @Override
    public String render(ResultRow row, HandlerConfig config) {
        String identifier = config.text(IDENTIFIER).isEmpty() ? DEFAULT_IDENTIFIER : config.text(IDENTIFIER);
        long id = ((Number) row.entity().id()).longValue();
        return search.ranked(row.entity().entityType(), row.input().getOrDefault(identifier, "")).stream()
                .filter(hit -> hit.entityId() == id).findFirst().map(SearchHit::excerpt).orElse("");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, LABEL, "Label", settings),
                Settings.text(prefix, IDENTIFIER, "Name the keywords are given under", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LABEL, Settings.submitted(prefix, LABEL, submitted));
        values.put(IDENTIFIER, Settings.submitted(prefix, IDENTIFIER, submitted));
        return values;
    }
}
