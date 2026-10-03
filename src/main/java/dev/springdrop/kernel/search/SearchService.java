package dev.springdrop.kernel.search;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Searches the index through the backend {@code search.settings} names, the
 * Postgres backend when it names none the site has.
 */
@Service
public class SearchService {

    /** The most hits a ranked search gives, before access is checked. */
    public static final int MAX_RESULTS = 500;

    private static final String RANKED_ATTRIBUTE = SearchService.class.getName() + ".ranked.";

    private final Map<String, SearchBackend> backends;
    private final ConfigStore configStore;

    public SearchService(List<SearchBackend> backends, ConfigStore configStore) {
        this.backends = backends.stream().collect(Collectors.toMap(SearchBackend::id, Function.identity()));
        this.configStore = configStore;
    }

    /** The ids of the backends the site has, in order. */
    public List<String> backendIds() {
        return backends.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    /** The backend the site searches now. */
    public SearchBackend backend() {
        String chosen = configStore.read(SearchSettings.CONFIG_NAME, SearchSettings.class, SearchSettings.DEFAULTS)
                .backend();
        return backends.getOrDefault(chosen, backends.get(PostgresSearchBackend.ID));
    }

    /** Makes the site search through another backend, whose index {@link SearchIndexer#reindex} then fills. */
    public void choose(String backendId) {
        if (!backends.containsKey(backendId)) {
            throw new IllegalArgumentException("The site has no search backend " + backendId + ".");
        }
        configStore.save(SearchSettings.CONFIG_NAME, new SearchSettings(backendId));
    }

    public SearchResults search(SearchQuery query) {
        return backend().search(query);
    }

    /**
     * The best {@link #MAX_RESULTS} hits for the keywords in an entity type's
     * documents, best first. During a request the hits are kept on it, so the
     * handlers of one view drawing ask the backend once.
     */
    public List<SearchHit> ranked(String entityType, String keywords) {
        List<String> words = SearchKeywords.words(keywords);
        if (words.isEmpty()) {
            return List.of();
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        String key = RANKED_ATTRIBUTE + entityType + "|" + String.join(" ", words);
        if (request != null && request.getAttribute(key, RequestAttributes.SCOPE_REQUEST) instanceof List<?> kept) {
            return kept.stream().map(SearchHit.class::cast).toList();
        }
        List<SearchHit> hits = backend().search(new SearchQuery(entityType, keywords, 0, MAX_RESULTS)).hits();
        if (request != null) {
            request.setAttribute(key, hits, RequestAttributes.SCOPE_REQUEST);
        }
        return hits;
    }
}
