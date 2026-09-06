package dev.springdrop.kernel.ban;

import java.util.List;

/**
 * The addresses this site refuses to answer, stored as the
 * {@code system.banned_ips} config object.
 */
public record BannedAddresses(List<String> addresses) {

    public static final String CONFIG_NAME = "system.banned_ips";

    public static final BannedAddresses NONE = new BannedAddresses(List.of());
}
