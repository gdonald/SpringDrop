package dev.springdrop.kernel.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.table;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Locale;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;

@SpringBootTest
class TranslatorIntegrationTest extends AbstractIntegrationTest {

    private static final String D = Translator.PLURAL_DELIMITER;

    @Autowired
    private Translator translator;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void noStrings() {
        dsl.deleteFrom(table("locale_source")).execute();
        translator.deleteTranslations("fr");
    }

    @AfterEach
    void englishAgain() {
        LocaleContextHolder.resetLocaleContext();
        dsl.deleteFrom(table("locale_source")).execute();
        translator.deleteTranslations("fr");
    }

    @Test
    void englishIsTheSourceAndIsNotRecorded() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(translator.t("Read more")).isEqualTo("Read more");
        assertThat(translator.count("fr", "", false)).isZero();
    }

    @Test
    void anUntranslatedStringIsShownInEnglishAndRecorded() {
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(translator.t("Read more")).isEqualTo("Read more");
        assertThat(translator.strings("fr", "", true, 0, 10)).extracting(TranslatableString::source)
                .containsExactly("Read more");
    }

    @Test
    void aTranslationIsShownInItsLanguageAndContext() {
        translator.save("fr", "May", "Long month name", "Mai");
        translator.save("fr", "May", "", "Peut");
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(translator.t("May", "Long month name")).isEqualTo("Mai");
        assertThat(translator.t("May")).isEqualTo("Peut");
        assertThat(translator.translation("fr", "May", "Long month name")).contains("Mai");
    }

    @Test
    void savingAnEmptyTranslationRemovesIt() {
        translator.save("fr", "Read more", "", "Lire la suite");
        translator.save("fr", "Read more", "", "");
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(translator.t("Read more")).isEqualTo("Read more");
        assertThat(translator.translated("fr")).isEmpty();
    }

    @Test
    void aPluralPicksItsFormByTheCount() {
        translator.save("fr", "1 comment" + D + "@count comments", "", "1 commentaire" + D + "@count commentaires");
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(translator.plural(1, "1 comment", "@count comments")).isEqualTo("1 commentaire");
        assertThat(translator.plural(4, "1 comment", "@count comments")).isEqualTo("4 commentaires");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(translator.plural(4, "1 comment", "@count comments")).isEqualTo("4 comments");
        translator.save("fr", "1 item" + D + "@count items", "", "un seul");
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(translator.plural(4, "1 item", "@count items")).isEqualTo("un seul");
    }

    @Test
    void theStringsAreSearchedInTheSourceAndTheTranslationAndPaged() {
        translator.save("fr", "Read more", "", "Lire la suite");
        translator.record("Next", "");
        translator.record("Previous", "");

        assertThat(translator.strings("fr", "", false, 0, 10)).extracting(TranslatableString::source)
                .containsExactly("Next", "Previous", "Read more");
        assertThat(translator.strings("fr", "suite", false, 0, 10)).extracting(TranslatableString::source)
                .containsExactly("Read more");
        assertThat(translator.strings("fr", "PREV", false, 0, 10)).extracting(TranslatableString::source)
                .containsExactly("Previous");
        assertThat(translator.count("fr", "", true)).isEqualTo(2);
        assertThat(translator.strings("fr", "", false, 1, 1)).extracting(TranslatableString::source)
                .containsExactly("Previous");
        long id = translator.record("Read more", "");
        assertThat(translator.string(id, "fr")).map(TranslatableString::translation).contains("Lire la suite");
        assertThat(translator.string(id + 1000, "fr")).isEmpty();
    }

    @Test
    void importedEntriesAreStoredAsTranslations() {
        assertThat(translator.importEntries("fr", List.of(new PoFile.Entry("", "Next", "Suivant"),
                new PoFile.Entry("", "Next", "Suivante")))).isEqualTo(2);
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(translator.t("Next")).isEqualTo("Suivante");
    }
}
