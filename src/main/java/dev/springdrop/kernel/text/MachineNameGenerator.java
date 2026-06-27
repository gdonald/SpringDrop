package dev.springdrop.kernel.text;

import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Generates machine names from human labels: transliterate to ASCII, lowercase,
 * and collapse runs of non-alphanumeric characters to single underscores. The
 * unique variant suffixes {@code _2}, {@code _3}, ... until the name is free.
 */
@Component
public class MachineNameGenerator {

    private final TransliterationService transliteration;

    public MachineNameGenerator(TransliterationService transliteration) {
        this.transliteration = transliteration;
    }

    public String generate(String label) {
        String ascii = transliteration.transliterate(label).toLowerCase(Locale.ROOT);
        String machine = ascii.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return machine.isEmpty() ? "_" : machine;
    }

    public String generateUnique(String label, Predicate<String> taken) {
        String base = generate(label);
        if (!taken.test(base)) {
            return base;
        }
        int suffix = 2;
        while (taken.test(base + "_" + suffix)) {
            suffix++;
        }
        return base + "_" + suffix;
    }
}
