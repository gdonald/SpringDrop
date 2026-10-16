package dev.springdrop.kernel.config.sync;

import java.util.List;

/** An import refused before anything was applied, with every problem that refused it. */
public class ConfigImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> problems;

    public ConfigImportException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
