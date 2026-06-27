package dev.springdrop.kernel.text;

import java.text.Normalizer;
import org.springframework.stereotype.Component;

/**
 * Maps text toward ASCII by decomposing accented characters and stripping the
 * combining marks, so "Crème" becomes "Creme". Used for slugs and machine names.
 */
@Component
public class TransliterationService {

    public String transliterate(String input) {
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
