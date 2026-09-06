package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RedirectSafetyTest {

    private final RedirectSafety safety =
            new RedirectSafety(new RedirectProperties(List.of("partner.example")));

    @Test
    void aPathOnThisSiteIsSafe() {
        assertThat(safety.isSafe("/admin/people")).isTrue();
    }

    @Test
    void theFrontPageIsSafe() {
        assertThat(safety.isSafe("/")).isTrue();
    }

    @Test
    void aProtocolRelativeUrlDressedAsAPathIsRefused() {
        assertThat(safety.isSafe("//elsewhere.example/landing")).isFalse();
    }

    @Test
    void aBackslashAfterTheSlashIsRefused() {
        assertThat(safety.isSafe("/\\elsewhere.example/landing")).isFalse();
    }

    @Test
    void nothingAtAllIsRefused() {
        assertThat(safety.isSafe(null)).isFalse();
        assertThat(safety.isSafe("   ")).isFalse();
    }

    @Test
    void anAllowlistedHostIsSafeOverEitherHttpScheme() {
        assertThat(safety.isSafe("https://partner.example/welcome")).isTrue();
        assertThat(safety.isSafe("http://PARTNER.EXAMPLE/welcome")).isTrue();
    }

    @Test
    void aHostNobodyAllowlistedIsRefused() {
        assertThat(safety.isSafe("https://elsewhere.example/landing")).isFalse();
    }

    @Test
    void aSchemeThatIsNotHttpIsRefused() {
        assertThat(safety.isSafe("ftp://partner.example/file")).isFalse();
        assertThat(safety.isSafe("javascript:alert(1)")).isFalse();
    }

    @Test
    void aRelativeReferenceWithNoSchemeIsRefused() {
        assertThat(safety.isSafe("elsewhere.example/landing")).isFalse();
    }

    @Test
    void anAbsoluteUrlNamingNoHostIsRefused() {
        assertThat(safety.isSafe("http:///landing")).isFalse();
    }

    @Test
    void somethingThatIsNotAUrlAtAllIsRefused() {
        assertThat(safety.isSafe("http://[nonsense")).isFalse();
    }

    @Test
    void anUnsafeDestinationFallsBackToTheOneNominated() {
        assertThat(safety.destinationOr("https://elsewhere.example", "/")).isEqualTo("/");
        assertThat(safety.destinationOr("/admin", "/")).isEqualTo("/admin");
    }
}
