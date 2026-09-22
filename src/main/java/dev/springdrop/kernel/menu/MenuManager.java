package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The site's menus. Four are created on install and locked, because core links
 * hang in them: the main navigation, administration, footer, and account menus.
 * A site may add menus of its own and delete those again.
 */
@Component
public class MenuManager {

    private final ConfigStore configStore;

    public MenuManager(ConfigStore configStore) {
        this.configStore = configStore;
    }

    /** Creates the menus a site always has, leaving any that are there alone. */
    public void install() {
        reserve(MenuConfig.MAIN, "Main navigation",
                "The links a visitor finds their way around the site with.");
        reserve(MenuConfig.ADMIN, "Administration",
                "The links behind the administration pages.");
        reserve(MenuConfig.FOOTER, "Footer",
                "The links at the bottom of every page.");
        reserve(MenuConfig.ACCOUNT, "User account menu",
                "Signing in and out, and the pages an account keeps to itself.");
    }

    public void save(MenuConfig menu) {
        configStore.save(MenuConfig.configName(menu.id()), menu);
    }

    public Optional<MenuConfig> find(String id) {
        return Optional.ofNullable(configStore.read(MenuConfig.configName(id), MenuConfig.class, null));
    }

    /** Removes a menu. A locked menu stays, since core links hang in it. */
    public boolean delete(String id) {
        if (find(id).map(MenuConfig::locked).orElse(false)) {
            return false;
        }
        configStore.delete(MenuConfig.configName(id));
        return true;
    }

    /** Every menu, listed by label. */
    public List<MenuConfig> all() {
        List<MenuConfig> menus = new ArrayList<>();
        for (String name : configStore.listNames(MenuConfig.CONFIG_PREFIX)) {
            find(name.substring(MenuConfig.CONFIG_PREFIX.length() + 1)).ifPresent(menus::add);
        }
        return menus.stream().sorted(Comparator.comparing(MenuConfig::label)).toList();
    }

    private void reserve(String id, String label, String description) {
        if (find(id).isEmpty()) {
            save(MenuConfig.of(id, label, description).asLocked());
        }
    }
}
