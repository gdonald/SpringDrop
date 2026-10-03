package dev.springdrop.kernel.site;

/**
 * Site-wide identity: the name and slogan shown in the theme, the address mail
 * is sent from, the site's absolute URL, and the path whose page the site's
 * home shows. Stored as the {@code system.site} config object.
 *
 * <p>A front page left blank, or set to the home path itself, is the listing of
 * front page content.
 */
public record SiteInformation(String name, String slogan, String mail, String url, String frontPage) {

    public static final String CONFIG_NAME = "system.site";

    /** The listing of promoted content, the front page a site starts with. */
    public static final String DEFAULT_FRONT_PAGE = "/node";

    public static final SiteInformation DEFAULTS =
            new SiteInformation("SpringDrop", "", "admin@example.com", "http://localhost:8080");

    public SiteInformation {
        frontPage = (frontPage == null || frontPage.isBlank() || frontPage.equals("/"))
                ? DEFAULT_FRONT_PAGE
                : frontPage;
    }

    public SiteInformation(String name, String slogan, String mail, String url) {
        this(name, slogan, mail, url, DEFAULT_FRONT_PAGE);
    }

    public SiteInformation withFrontPage(String path) {
        return new SiteInformation(name, slogan, mail, url, path);
    }
}
