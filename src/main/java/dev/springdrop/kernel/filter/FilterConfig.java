package dev.springdrop.kernel.filter;

import java.util.Map;

/** One filter a format runs: which plugin, where it comes in the pipeline, and its settings. */
public record FilterConfig(String id, int weight, Map<String, Object> settings) {

    public FilterConfig {
        settings = Map.copyOf(settings);
    }

    public static FilterConfig of(String id, int weight) {
        return new FilterConfig(id, weight, Map.of());
    }
}
