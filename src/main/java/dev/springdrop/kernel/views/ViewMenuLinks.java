package dev.springdrop.kernel.views;

import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The menu links page displays add: one for each page display with a menu
 * title, in the menu its {@code menu} setting names, the main menu by default,
 * leading to the display's address. A link's id is {@code views.<view>.<display>}.
 */
@Component
public class ViewMenuLinks implements MenuLinkProvider {

    private final ViewManager views;

    public ViewMenuLinks(ViewManager views) {
        this.views = views;
    }

    @Override
    public List<MenuLink> menuLinks() {
        List<MenuLink> links = new ArrayList<>();
        for (ViewConfig view : views.all()) {
            for (ViewDisplay display : view.displays()) {
                String title = display.text(ViewDisplay.MENU_TITLE);
                if (display.plugin().equals(ViewDisplay.PAGE) && !title.isEmpty()) {
                    String menu = display.text(ViewDisplay.MENU);
                    links.add(MenuLink.of("views." + view.id() + "." + display.id(),
                            menu.isEmpty() ? MenuConfig.MAIN : menu, title, ViewPaths.address(display)));
                }
            }
        }
        return links;
    }
}
