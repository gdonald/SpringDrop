package dev.springdrop.kernel.search;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The site's search pages. When the site starts it adds a content search at
 * {@code /search/node} and an account search at {@code /search/user}, with the
 * views they draw, leaving any already saved as they are.
 */
@Component
public class SearchPageManager {

    public static final String PATH = "/search";

    public static final String CONTENT = "node_search";

    public static final String USERS = "user_search";

    private final ConfigStore configStore;
    private final SearchViews searchViews;

    public SearchPageManager(ConfigStore configStore, SearchViews searchViews) {
        this.configStore = configStore;
        this.searchViews = searchViews;
    }

    public void save(SearchPage page) {
        configStore.save(SearchPage.configName(page.id()), page);
    }

    public Optional<SearchPage> find(String id) {
        return Optional.ofNullable(configStore.read(SearchPage.configName(id), SearchPage.class, null));
    }

    public void delete(String id) {
        configStore.delete(SearchPage.configName(id));
    }

    /** Every search page, by weight, then label. */
    public List<SearchPage> all() {
        List<SearchPage> pages = new ArrayList<>();
        for (String name : configStore.listNames(SearchPage.CONFIG_PREFIX)) {
            find(name.substring(SearchPage.CONFIG_PREFIX.length() + 1)).ifPresent(pages::add);
        }
        return pages.stream().sorted(Comparator.comparingInt(SearchPage::weight).thenComparing(SearchPage::label))
                .toList();
    }

    /** The search page answering at a path under {@code /search}. */
    public Optional<SearchPage> atPath(String path) {
        return all().stream().filter(page -> page.path().equals(path)).findFirst();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void install() {
        searchViews.install();
        for (SearchPage page : List.of(new SearchPage(CONTENT, "Content", "node", SearchViews.CONTENT, 0),
                new SearchPage(USERS, "Users", "user", SearchViews.USERS, 1))) {
            if (find(page.id()).isEmpty()) {
                save(page);
            }
        }
    }
}
