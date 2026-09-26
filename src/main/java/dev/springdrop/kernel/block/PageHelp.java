package dev.springdrop.kernel.block;

import java.util.Optional;

/**
 * Help text for the pages a module serves, shown by the help block. A module
 * registers one as a bean and answers for its own paths.
 */
@FunctionalInterface
public interface PageHelp {

    Optional<String> helpFor(String path);
}
