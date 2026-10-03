package dev.springdrop.kernel.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the Lucene backend keeps its index on disk. A relative path is read
 * from the directory the application runs in.
 */
@ConfigurationProperties("springdrop.search")
public record SearchProperties(@DefaultValue("search-index") String lucenePath) {
}
