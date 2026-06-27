package dev.springdrop.kernel.module;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ModuleRegistryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ModuleRegistry moduleRegistry;

    @Test
    void loadsTheDescriptorsFromTheClasspath() {
        assertThat(moduleRegistry.all()).extracting(ModuleInfo::name)
                .contains("foundation", "content", "blog");
        assertThat(moduleRegistry.find("blog")).get()
                .extracting(ModuleInfo::label).isEqualTo("Blog");
    }

    @Test
    void resolvesAnInstallOrderWithDependenciesFirst() {
        List<String> order = moduleRegistry.installOrder().stream().map(ModuleInfo::name).toList();

        assertThat(order.indexOf("foundation")).isLessThan(order.indexOf("content"));
        assertThat(order.indexOf("content")).isLessThan(order.indexOf("blog"));
    }
}
