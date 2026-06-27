package dev.springdrop.kernel.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MachineNameGeneratorTest {

    private final MachineNameGenerator generator = new MachineNameGenerator(new TransliterationService());

    @Test
    void transliteratesLowercasesAndUnderscores() {
        assertThat(generator.generate("Crème Brûlée!")).isEqualTo("creme_brulee");
    }

    @Test
    void fallsBackForLabelsWithNoUsableCharacters() {
        assertThat(generator.generate("!!!")).isEqualTo("_");
    }

    @Test
    void returnsTheBaseNameWhenItIsFree() {
        assertThat(generator.generateUnique("Article", name -> false)).isEqualTo("article");
    }

    @Test
    void suffixesUntilTheNameIsUnique() {
        Set<String> taken = Set.of("article", "article_2");
        assertThat(generator.generateUnique("Article", taken::contains)).isEqualTo("article_3");
    }
}
