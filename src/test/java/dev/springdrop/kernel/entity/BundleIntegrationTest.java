package dev.springdrop.kernel.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class BundleIntegrationTest extends AbstractIntegrationTest {

    record Doodad(long id, String label) {
    }

    record DoodadType(String id, String label) {
    }

    static final EntityType DOODAD = EntityType.content("doodad", Doodad.class)
            .withBundles("type", "doodad_type");

    static final EntityType DOODAD_TYPE = EntityType.config("doodad_type", DoodadType.class);

    static final EntityType UNBUNDLED = EntityType.content("thingamajig", Doodad.class);

    private static final BundleDefinition SIMPLE = new BundleDefinition("simple", "Simple doodad");

    private static final BundleDefinition ELABORATE = new BundleDefinition("elaborate", "Elaborate doodad");

    @Autowired
    private BundleManager bundleManager;

    @BeforeEach
    void twoBundlesOfOneType() {
        bundleManager.save("doodad", SIMPLE);
        bundleManager.save("doodad", ELABORATE);
    }

    @AfterEach
    void removeEveryBundle() {
        bundleManager.bundles("doodad").forEach(bundle -> bundleManager.delete("doodad", bundle.id()));
    }

    @Test
    void aBundleIsFoundByItsMachineName() {
        assertThat(bundleManager.find("doodad", "simple")).contains(SIMPLE);
    }

    @Test
    void anUnknownBundleHasNoDefinition() {
        assertThat(bundleManager.find("doodad", "nonesuch")).isEmpty();
    }

    @Test
    void everyBundleOfATypeIsListed() {
        assertThat(bundleManager.bundles("doodad")).containsExactlyInAnyOrder(SIMPLE, ELABORATE);
    }

    @Test
    void aDeletedBundleIsGone() {
        bundleManager.save("doodad", new BundleDefinition("temporary", "Temporary"));

        bundleManager.delete("doodad", "temporary");

        assertThat(bundleManager.find("doodad", "temporary")).isEmpty();
    }

    @Test
    void anUnbundledTypeHasNoBundlesToAskFor() {
        assertThatThrownBy(() -> bundleManager.bundles("thingamajig"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not have bundles");
    }

    @Test
    void aBundledTypeCarriesItsBundleKeyAndBundleEntityType() {
        assertThat(DOODAD.keys().bundle()).isEqualTo("type");
        assertThat(DOODAD.bundleEntityType()).isEqualTo("doodad_type");
    }

    @TestConfiguration
    static class BundledTypes {

        @Bean
        EntityTypeProvider doodadTypes() {
            return () -> List.of(DOODAD, DOODAD_TYPE, UNBUNDLED);
        }
    }
}
