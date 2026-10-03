package dev.springdrop.kernel.search;

import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FilterHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.Settings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the results whose documents in the search index hold every keyword,
 * ranked best first. The {@code entity_type} setting names the documents
 * searched, and the property the filter reads holds their entity ids. Keywords
 * with no words find nothing.
 */
@SpringDropPlugin(id = SearchKeywordsFilter.ID, type = FilterHandler.class)
public class SearchKeywordsFilter implements FilterHandler {

    public static final String ID = "search_keywords";

    public static final String ENTITY_TYPE = "entity_type";

    private final SearchService search;
    private final SearchIndexer indexer;

    public SearchKeywordsFilter(SearchService search, SearchIndexer indexer) {
        this.search = search;
        this.indexer = indexer;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Search keywords";
    }

    private List<Object> rankedIds(HandlerConfig config, String value) {
        return search.ranked(config.text(ENTITY_TYPE), value).stream().map(hit -> (Object) hit.entityId())
                .toList();
    }

    @Override
    public Optional<Condition> condition(HandlerConfig config, String value) {
        return Optional.of(Condition.in(config.property(), rankedIds(config, value)));
    }

    @Override
    public Optional<Sort> sort(HandlerConfig config, String value) {
        return Optional.of(Sort.byPosition(config.property(), rankedIds(config, value)));
    }

    @Override
    public FormElement exposedElement(HandlerConfig config, String current) {
        String label = config.text("label").isEmpty() ? "Keywords" : config.text("label");
        return FormElement.of(ElementType.TEXTFIELD, FilterHandler.identifier(config)).label(label).value(current)
                .attribute("type", "search");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(
                Settings.select(prefix, ENTITY_TYPE, "Search the index of", settings, indexer.entityTypes().stream()
                        .map(type -> new SelectOption(type, type)).toList()),
                Settings.checkbox(prefix, EXPOSED, "Let the reader give the keywords", settings, false),
                Settings.text(prefix, VALUE, "Keywords", settings),
                Settings.text(prefix, IDENTIFIER, "Name the keywords are given under", settings),
                Settings.text(prefix, "label", "Label", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(ENTITY_TYPE, Settings.chosen(prefix, ENTITY_TYPE, submitted, indexer.entityTypes()));
        values.put(EXPOSED, Settings.ticked(prefix, EXPOSED, submitted));
        values.put(VALUE, Settings.submitted(prefix, VALUE, submitted));
        values.put(IDENTIFIER, Settings.submitted(prefix, IDENTIFIER, submitted));
        values.put("label", Settings.submitted(prefix, "label", submitted));
        return values;
    }
}
