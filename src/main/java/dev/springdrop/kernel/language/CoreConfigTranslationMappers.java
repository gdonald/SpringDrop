package dev.springdrop.kernel.language;

import dev.springdrop.kernel.contact.ContactEntityType;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.language.ConfigTranslationMapper.TranslatableKey;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.views.ViewConfig;
import java.util.List;
import org.springframework.stereotype.Component;

/** The config core lets a site translate: the names, help, and titles its own config objects show. */
@Component
public class CoreConfigTranslationMappers implements ConfigTranslationMappers {

    private static final TranslatableKey LABEL = new TranslatableKey("label", "Label");

    private static final TranslatableKey DESCRIPTION = new TranslatableKey("description", "Description");

    @Override
    public List<ConfigTranslationMapper> mappers() {
        return List.of(
                new ConfigTranslationMapper(FieldInstanceConfig.CONFIG_PREFIX, "Field",
                        List.of(LABEL, new TranslatableKey("description", "Help text"))),
                new ConfigTranslationMapper(NodeEntityType.TYPE_ID, "Content type", List.of(LABEL, DESCRIPTION,
                        new TranslatableKey("help", "Explanation or submission guidelines"),
                        new TranslatableKey("titleLabel", "Title field label"))),
                new ConfigTranslationMapper(TaxonomyEntityType.VOCABULARY_ID, "Vocabulary",
                        List.of(LABEL, DESCRIPTION)),
                new ConfigTranslationMapper(MenuConfig.CONFIG_PREFIX, "Menu", List.of(LABEL, DESCRIPTION)),
                new ConfigTranslationMapper(ViewConfig.CONFIG_PREFIX, "View", List.of(LABEL, DESCRIPTION,
                        new TranslatableKey("displays.*.title", "Display title"))),
                new ConfigTranslationMapper(ContactEntityType.FORM_ID, "Contact form",
                        List.of(LABEL, new TranslatableKey("reply", "Auto-reply"))),
                new ConfigTranslationMapper(SiteInformation.CONFIG_NAME, "Site information",
                        List.of(new TranslatableKey("name", "Site name"), new TranslatableKey("slogan", "Slogan"))));
    }
}
