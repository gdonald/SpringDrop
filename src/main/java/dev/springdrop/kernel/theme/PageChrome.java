package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.render.Renderable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything around the content of a page: what the site is called, what the
 * page is called, and the navigation, breadcrumbs, local tasks, local actions,
 * messages, and pager the theme draws around it, and the blocks placed in each
 * region of the layout.
 *
 * <p>Each part is optional. A page that sets none of them renders as a title and
 * its content inside the theme's shell.
 */
public record PageChrome(
        String siteName,
        String slogan,
        String title,
        List<Link> primaryNavigation,
        List<Link> breadcrumbs,
        List<Tab> tabs,
        List<Link> localActions,
        List<StatusMessage> messages,
        Optional<Pager> pager,
        Map<String, Renderable> regions) {

    public PageChrome {
        primaryNavigation = List.copyOf(primaryNavigation);
        breadcrumbs = List.copyOf(breadcrumbs);
        tabs = List.copyOf(tabs);
        localActions = List.copyOf(localActions);
        messages = List.copyOf(messages);
        regions = Map.copyOf(regions);
    }

    public static PageChrome of(String siteName, String title) {
        return new PageChrome(siteName, "", title,
                List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Map.of());
    }

    public PageChrome withSlogan(String text) {
        return new PageChrome(siteName, text, title,
                primaryNavigation, breadcrumbs, tabs, localActions, messages, pager, regions);
    }

    public PageChrome withPrimaryNavigation(List<Link> links) {
        return new PageChrome(siteName, slogan, title,
                links, breadcrumbs, tabs, localActions, messages, pager, regions);
    }

    public PageChrome withBreadcrumbs(List<Link> trail) {
        return new PageChrome(siteName, slogan, title,
                primaryNavigation, trail, tabs, localActions, messages, pager, regions);
    }

    public PageChrome withTabs(List<Tab> localTasks) {
        return new PageChrome(siteName, slogan, title,
                primaryNavigation, breadcrumbs, localTasks, localActions, messages, pager, regions);
    }

    public PageChrome withLocalActions(List<Link> actions) {
        return new PageChrome(siteName, slogan, title,
                primaryNavigation, breadcrumbs, tabs, actions, messages, pager, regions);
    }

    public PageChrome withMessage(StatusMessage message) {
        List<StatusMessage> combined = new ArrayList<>(messages);
        combined.add(message);
        return new PageChrome(siteName, slogan, title,
                primaryNavigation, breadcrumbs, tabs, localActions, combined, pager, regions);
    }

    public PageChrome withPager(Pager listingPager) {
        return new PageChrome(siteName, slogan, title,
                primaryNavigation, breadcrumbs, tabs, localActions, messages, Optional.of(listingPager),
                regions);
    }

    /** Puts what a region holds on the page, replacing anything it held before. */
    public PageChrome withRegion(String region, Renderable content) {
        Map<String, Renderable> combined = new LinkedHashMap<>(regions);
        combined.put(region, content);
        return new PageChrome(siteName, slogan, title,
                primaryNavigation, breadcrumbs, tabs, localActions, messages, pager, combined);
    }
}
