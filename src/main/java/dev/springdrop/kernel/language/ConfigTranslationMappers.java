package dev.springdrop.kernel.language;

import java.util.List;

/** Contributes the kinds of config that can be translated. A module registers a bean of this type. */
public interface ConfigTranslationMappers {

    List<ConfigTranslationMapper> mappers();
}
