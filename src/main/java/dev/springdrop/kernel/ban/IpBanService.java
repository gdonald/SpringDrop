package dev.springdrop.kernel.ban;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The site's ban list. An address is held once however many times it is banned,
 * and unbanning an address that was never banned leaves the list as it was.
 */
@Component
public class IpBanService {

    private final ConfigStore config;

    public IpBanService(ConfigStore config) {
        this.config = config;
    }

    public List<String> banned() {
        return config.read(BannedAddresses.CONFIG_NAME, BannedAddresses.class, BannedAddresses.NONE)
                .addresses();
    }

    public boolean isBanned(String address) {
        return banned().contains(address);
    }

    public void ban(String address) {
        if (address == null || address.isBlank() || isBanned(address.strip())) {
            return;
        }
        List<String> addresses = new ArrayList<>(banned());
        addresses.add(address.strip());
        save(addresses);
    }

    public void unban(String address) {
        List<String> addresses = new ArrayList<>(banned());
        if (addresses.remove(address)) {
            save(addresses);
        }
    }

    private void save(List<String> addresses) {
        config.save(BannedAddresses.CONFIG_NAME, new BannedAddresses(List.copyOf(addresses)));
    }
}
