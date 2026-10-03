package dev.springdrop.kernel.search;

/** The search settings, stored as the config object {@code search.settings}: the backend the site searches. */
public record SearchSettings(String backend) {

    public static final String CONFIG_NAME = "search.settings";

    public static final SearchSettings DEFAULTS = new SearchSettings(PostgresSearchBackend.ID);
}
