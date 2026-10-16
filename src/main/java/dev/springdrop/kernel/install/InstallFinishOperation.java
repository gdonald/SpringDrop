package dev.springdrop.kernel.install;

import dev.springdrop.kernel.batch.BatchOperationHandler;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.update.UpdateManager;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserEntityType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The installer's last step: adds the chosen language when it is not English
 * and makes it the default, saves the site's name and address, gives the
 * first account the administrator's name, address, and password, records
 * every update routine as applied, since the site starts current, and marks
 * the site installed.
 */
@Component
public class InstallFinishOperation implements BatchOperationHandler {

    public static final String ID = "install.finish";

    static final String LANGCODE = "langcode";

    static final String SITE_NAME = "site_name";

    static final String SITE_MAIL = "site_mail";

    static final String ADMIN_NAME = "admin_name";

    static final String ADMIN_MAIL = "admin_mail";

    static final String PASSWORD_HASH = "password_hash";

    private static final Set<String> RIGHT_TO_LEFT = Set.of("ar", "fa", "he", "ur", "yi");

    private final LanguageManager languages;
    private final ConfigStore config;
    private final EntityCrudService entities;
    private final InstallState state;
    private final UpdateManager updates;

    public InstallFinishOperation(LanguageManager languages, ConfigStore config, EntityCrudService entities,
            InstallState state, UpdateManager updates) {
        this.updates = updates;
        this.languages = languages;
        this.config = config;
        this.entities = entities;
        this.state = state;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void run(Map<String, Object> payload) {
        String langcode = (String) payload.get(LANGCODE);
        if (languages.find(langcode).isEmpty()) {
            languages.save(new LanguageConfig(langcode, Locale.of(langcode).getDisplayLanguage(Locale.ENGLISH),
                    RIGHT_TO_LEFT.contains(langcode) ? LanguageConfig.RTL : LanguageConfig.LTR, 0, langcode, ""));
        }
        languages.makeDefault(langcode);
        SiteInformation site = config.read(SiteInformation.CONFIG_NAME, SiteInformation.class,
                SiteInformation.DEFAULTS);
        config.save(SiteInformation.CONFIG_NAME, new SiteInformation((String) payload.get(SITE_NAME), site.slogan(),
                (String) payload.get(SITE_MAIL), site.url(), site.frontPage()));
        EntityData administrator = entities.load(UserEntityType.ID, UserAccount.ADMINISTRATOR_ID).orElseThrow();
        Map<String, Object> fields = new LinkedHashMap<>(administrator.fields());
        fields.put(UserEntityType.MAIL, payload.get(ADMIN_MAIL));
        fields.put(UserEntityType.PASSWORD_HASH, payload.get(PASSWORD_HASH));
        fields.put("status", true);
        entities.save(new EntityData(UserEntityType.ID, administrator.id(), administrator.uuid(), null,
                (String) payload.get(ADMIN_NAME), administrator.langcode(), null, fields));
        updates.markCurrent();
        state.markInstalled();
    }
}
