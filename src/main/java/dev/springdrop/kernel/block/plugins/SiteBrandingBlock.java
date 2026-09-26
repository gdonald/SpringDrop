package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.site.SiteInformation;
import java.util.Map;
import java.util.Optional;

/** The site's name, linked to the front page, and its slogan. */
@SpringDropPlugin(id = SiteBrandingBlock.ID, type = BlockPlugin.class)
public class SiteBrandingBlock implements BlockPlugin {

    public static final String ID = "system_branding_block";

    private final ConfigStore configStore;

    public SiteBrandingBlock(ConfigStore configStore) {
        this.configStore = configStore;
    }

    @Override
    public String label() {
        return "Site branding";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
        return Optional.of(Renderable.of(BlockTemplates.ELEMENTS, "branding")
                .with("siteName", site.name())
                .with("slogan", site.slogan()));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY.withTag("config:" + SiteInformation.CONFIG_NAME);
    }
}
