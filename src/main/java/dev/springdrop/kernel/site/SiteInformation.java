package dev.springdrop.kernel.site;

/**
 * Site-wide identity: the name and slogan shown in the theme, the address mail
 * is sent from, and the site's absolute URL. Stored as the {@code system.site}
 * config object.
 */
public record SiteInformation(String name, String slogan, String mail, String url) {

    public static final String CONFIG_NAME = "system.site";

    public static final SiteInformation DEFAULTS =
            new SiteInformation("SpringDrop", "", "admin@example.com", "http://localhost:8080");
}
