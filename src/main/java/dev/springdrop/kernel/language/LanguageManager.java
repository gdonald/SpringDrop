package dev.springdrop.kernel.language;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** The languages the site has, which one is its default, and which one the current request is in. */
@Component
public class LanguageManager {

    public static final String ENGLISH = "en";

    /** A language code: two or three letters, and optionally a region or script after a hyphen. */
    public static final String LANGCODE_PATTERN = "[a-z]{2,3}(-[a-z0-9]{2,8})?";

    private final ConfigStore config;

    public LanguageManager(ConfigStore config) {
        this.config = config;
    }

    /** Adds English, the language a site starts with, when the site has no languages yet. */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        if (all().isEmpty()) {
            save(new LanguageConfig(ENGLISH, "English", LanguageConfig.LTR, 0, "", ""));
        }
    }

    /** The languages, by weight and then name. */
    public List<LanguageConfig> all() {
        return config.listNames(LanguageConfig.CONFIG_PREFIX).stream()
                .map(name -> config.read(name, LanguageConfig.class, null))
                .sorted(Comparator.comparingInt(LanguageConfig::weight).thenComparing(LanguageConfig::label))
                .toList();
    }

    public Optional<LanguageConfig> find(String langcode) {
        return Optional.ofNullable(config.read(LanguageConfig.configName(langcode), LanguageConfig.class, null));
    }

    public void save(LanguageConfig language) {
        config.save(LanguageConfig.configName(language.id()), language);
    }

    /** Deletes a language, refusing the default one. Gives whether it was deleted. */
    public boolean delete(String langcode) {
        if (langcode.equals(defaultLangcode()) || find(langcode).isEmpty()) {
            return false;
        }
        config.delete(LanguageConfig.configName(langcode));
        return true;
    }

    public String defaultLangcode() {
        return config.read(LanguageSettings.CONFIG_NAME, LanguageSettings.class, LanguageSettings.DEFAULTS)
                .defaultLangcode();
    }

    public void makeDefault(String langcode) {
        config.save(LanguageSettings.CONFIG_NAME, new LanguageSettings(langcode));
    }

    /** The language the current request was negotiated into, or the default one. */
    public LanguageConfig current() {
        String tag = LocaleContextHolder.getLocale().toLanguageTag().toLowerCase(Locale.ROOT);
        return find(tag).or(() -> find(defaultLangcode()))
                .orElseGet(() -> new LanguageConfig(tag, tag, LanguageConfig.LTR, 0, "", ""));
    }
}
