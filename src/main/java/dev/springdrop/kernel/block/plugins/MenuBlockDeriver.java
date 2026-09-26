package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.menu.MenuNavigation;
import dev.springdrop.kernel.plugin.DerivablePlugin;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.LinkedHashMap;
import java.util.Map;

/** Derives a menu block for every menu the site has, as {@code system_menu_block:<menu>}. */
@SpringDropPlugin(id = MenuBlockDeriver.ID, type = BlockPlugin.class)
public class MenuBlockDeriver implements DerivablePlugin<BlockPlugin> {

    public static final String ID = "system_menu_block";

    /** The primary menu: the block for the main navigation. */
    public static final String PRIMARY = ID + ":" + MenuConfig.MAIN;

    private final MenuManager menus;
    private final MenuNavigation navigation;

    public MenuBlockDeriver(MenuManager menus, MenuNavigation navigation) {
        this.menus = menus;
        this.navigation = navigation;
    }

    @Override
    public Map<String, BlockPlugin> derivatives() {
        Map<String, BlockPlugin> blocks = new LinkedHashMap<>();
        for (MenuConfig menu : menus.all()) {
            blocks.put(menu.id(), new MenuBlock(menu, navigation));
        }
        return blocks;
    }
}
