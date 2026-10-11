package dev.springdrop.kernel.language;

import dev.springdrop.kernel.config.ConfigChangedEvent;
import dev.springdrop.kernel.config.ConfigOverrideProvider;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Shows config in the request's language: a config object translated into it
 * reads with its translated strings. English is the language config is
 * written in, so an English request reads it as stored. An administration
 * page reads config as stored too, so editing an object in another language
 * never writes a translation over its source. Which objects each language
 * translates is kept in memory, read again when a translation changes.
 */
@Component
public class LanguageConfigOverrides implements ConfigOverrideProvider {

    private final ObjectProvider<ConfigTranslationManager> translations;
    private final ObjectProvider<ConfigStore> config;
    private final RouteRegistry routes;
    private final Map<String, Set<String>> translatedNames = new ConcurrentHashMap<>();

    public LanguageConfigOverrides(ObjectProvider<ConfigTranslationManager> translations,
            ObjectProvider<ConfigStore> config, RouteRegistry routes) {
        this.translations = translations;
        this.config = config;
        this.routes = routes;
    }

    @Override
    public Optional<Map<String, Object>> overrides(String configName) {
        String langcode = LocaleContextHolder.getLocale().toLanguageTag().toLowerCase(Locale.ROOT);
        if (langcode.equals(LanguageManager.ENGLISH) || configName.startsWith(ConfigTranslation.CONFIG_PREFIX + ".")
                || onAdministrationPage() || !translatedIn(langcode).contains(configName)) {
            return Optional.empty();
        }
        return translations.getObject().overrides(langcode, configName);
    }

    @EventListener
    void configChanged(ConfigChangedEvent event) {
        if (event.name().startsWith(ConfigTranslation.CONFIG_PREFIX + ".")) {
            translatedNames.clear();
        }
    }

    private Set<String> translatedIn(String langcode) {
        String prefix = ConfigTranslation.CONFIG_PREFIX + "." + langcode;
        return translatedNames.computeIfAbsent(langcode, code -> config.getObject().listNames(prefix).stream()
                .map(name -> name.substring(prefix.length() + 1))
                .collect(Collectors.toSet()));
    }

    private boolean onAdministrationPage() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
                && routes.match(request.getRequest().getRequestURI()).map(RouteDefinition::admin).orElse(false);
    }
}
