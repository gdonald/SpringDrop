package dev.springdrop.kernel.config.sync;

/**
 * The type a config object's value must read as: for one object named
 * exactly, or for every object under a prefix, {@code <prefix>.<anything>}.
 */
public record ConfigSchema(String name, boolean prefix, Class<?> type) {

    public static ConfigSchema named(String name, Class<?> type) {
        return new ConfigSchema(name, false, type);
    }

    public static ConfigSchema under(String prefix, Class<?> type) {
        return new ConfigSchema(prefix, true, type);
    }

    public boolean covers(String configName) {
        return prefix ? configName.startsWith(name + ".") : configName.equals(name);
    }
}
