package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedHostPropertiesTest {

    private static final List<String> SPRINGDROP_ONLY = List.of("^springdrop\\.example$");

    @Test
    void aSiteThatNamesNoHostsAnswersToAnyOfThem() {
        assertThat(new TrustedHostProperties(List.of()).trusts("elsewhere.example")).isTrue();
    }

    @Test
    void theNameIsMatchedWhateverItsCase() {
        assertThat(new TrustedHostProperties(SPRINGDROP_ONLY).trusts("SpringDrop.Example")).isTrue();
    }

    @Test
    void aNameNobodyListedIsNotTrusted() {
        assertThat(new TrustedHostProperties(SPRINGDROP_ONLY).trusts("elsewhere.example")).isFalse();
    }

    @Test
    void aRequestCarryingNoHostHeaderIsNotTrusted() {
        assertThat(new TrustedHostProperties(SPRINGDROP_ONLY).trusts(null)).isFalse();
    }

}
