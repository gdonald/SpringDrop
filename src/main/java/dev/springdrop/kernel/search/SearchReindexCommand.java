package dev.springdrop.kernel.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Builds the search index again when the site starts with
 * {@code --search-reindex}, once the site has installed what it needs.
 */
@Component
public class SearchReindexCommand {

    public static final String OPTION = "search-reindex";

    private static final Logger LOG = LoggerFactory.getLogger(SearchReindexCommand.class);

    private final ApplicationArguments arguments;
    private final SearchIndexer indexer;

    public SearchReindexCommand(ApplicationArguments arguments, SearchIndexer indexer) {
        this.arguments = arguments;
        this.indexer = indexer;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void run() {
        if (arguments.containsOption(OPTION)) {
            LOG.info("Indexed {} entities in the {} search backend.", indexer.reindex(),
                    indexer.backendId());
        }
    }
}
