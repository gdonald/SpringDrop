package dev.springdrop.kernel.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccessResultTest {

    @Test
    void anAllowedResultGrantsAccess() {
        assertThat(AccessResult.allow().allowed()).isTrue();
    }

    @Test
    void aForbiddenResultDeniesAccess() {
        assertThat(AccessResult.forbid().allowed()).isFalse();
    }

    @Test
    void aResultCollectsTheContextsItVariesBy() {
        AccessResult result = AccessResult.allow()
                .withCacheContext("user.permissions")
                .withCacheContext("url");

        assertThat(result.cacheContexts()).containsExactly("user.permissions", "url");
    }

    @Test
    void aResultCollectsTheTagsThatInvalidateIt() {
        AccessResult result = AccessResult.allow().withCacheTag("route:front").withCacheTag("node:42");

        assertThat(result.cacheTags()).containsExactly("route:front", "node:42");
    }

    @Test
    void aResultWithNoMaxAgeIsCacheableUntilItsTagsAreInvalidated() {
        AccessResult result = AccessResult.allow();

        assertThat(result.maxAge()).isEmpty();
        assertThat(result.isCacheable()).isTrue();
    }

    @Test
    void aResultWithAMaxAgeIsCacheableForThatLong() {
        AccessResult result = AccessResult.allow().withMaxAge(Duration.ofMinutes(5));

        assertThat(result.maxAge()).contains(Duration.ofMinutes(5));
        assertThat(result.isCacheable()).isTrue();
    }

    @Test
    void anUncacheableResultReportsAZeroMaxAge() {
        AccessResult result = AccessResult.allow().uncacheable();

        assertThat(result.maxAge()).contains(Duration.ZERO);
        assertThat(result.isCacheable()).isFalse();
    }

    @Test
    void combiningTwoAllowedResultsGrantsAccessAndUnionsTheirMetadata() {
        AccessResult combined = AccessResult.allow().withCacheContext("user.permissions").withCacheTag("route:front")
                .and(AccessResult.allow().withCacheContext("url").withCacheTag("node:42"));

        assertThat(combined.allowed()).isTrue();
        assertThat(combined.cacheContexts()).containsExactlyInAnyOrder("user.permissions", "url");
        assertThat(combined.cacheTags()).containsExactlyInAnyOrder("route:front", "node:42");
    }

    @Test
    void combiningWithAForbiddenResultDeniesAccess() {
        assertThat(AccessResult.allow().and(AccessResult.forbid()).allowed()).isFalse();
    }

    @Test
    void combiningAForbiddenResultWithAnAllowedOneDeniesAccess() {
        assertThat(AccessResult.forbid().and(AccessResult.allow()).allowed()).isFalse();
    }

    @Test
    void combiningKeepsTheShorterMaxAgeOfTheTwo() {
        AccessResult shorter = AccessResult.allow().withMaxAge(Duration.ofMinutes(1));
        AccessResult longer = AccessResult.allow().withMaxAge(Duration.ofHours(1));

        assertThat(shorter.and(longer).maxAge()).contains(Duration.ofMinutes(1));
        assertThat(longer.and(shorter).maxAge()).contains(Duration.ofMinutes(1));
    }

    @Test
    void combiningWithAResultThatHasNoMaxAgeKeepsTheOneThatDoes() {
        AccessResult limited = AccessResult.allow().withMaxAge(Duration.ofMinutes(1));
        AccessResult unlimited = AccessResult.allow();

        assertThat(unlimited.and(limited).maxAge()).contains(Duration.ofMinutes(1));
        assertThat(limited.and(unlimited).maxAge()).contains(Duration.ofMinutes(1));
    }

    @Test
    void combiningTwoResultsWithoutMaxAgesLeavesNoMaxAge() {
        assertThat(AccessResult.allow().and(AccessResult.allow()).maxAge()).isEqualTo(Optional.empty());
    }
}
