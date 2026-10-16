package dev.springdrop.kernel.install;

import java.util.List;

/**
 * A starting point the installer offers: the modules it enables and the config
 * it imports, from the YAML files under
 * {@code classpath:springdrop/profiles/<id>/config/}, one per object named
 * {@code <name>.yml} holding its value under {@code data}. A profile sets up
 * what plain config cannot in {@link #install()}.
 */
public interface InstallProfile {

    String id();

    String label();

    String description();

    /** Where the profile is listed among the others, lowest first. */
    int weight();

    /** The modules it enables, which bring their own dependencies along. */
    List<String> modules();

    /** Where its config files are: its own directory, and any other profile's it builds on. */
    default List<String> configLocations() {
        return List.of(configLocationOf(id()));
    }

    static String configLocationOf(String profileId) {
        return "classpath*:springdrop/profiles/" + profileId + "/config/*.yml";
    }

    /** Runs once the profile's modules are enabled and its config imported. */
    default void install() {
    }
}
