package dev.springdrop.kernel.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;

@SpringBootTest
class PluginRegistryIntegrationTest extends AbstractIntegrationTest {

    interface Greeter {
        String greet();
    }

    interface Formatter {
        String format(String value);
    }

    @Autowired
    private PluginRegistry pluginRegistry;

    @Test
    void discoversPluginsOfAContractTypeById() {
        PluginManager<Greeter> greeters = pluginRegistry.managerFor(Greeter.class);

        assertThat(greeters.get("english").greet()).isEqualTo("Hello");
        assertThat(greeters.get("french").greet()).isEqualTo("Bonjour");
        assertThat(greeters.has("english")).isTrue();
    }

    @Test
    void excludesPluginsOfADifferentContractType() {
        assertThat(pluginRegistry.managerFor(Greeter.class).has("upper")).isFalse();
        assertThat(pluginRegistry.managerFor(Formatter.class).get("upper").format("hi")).isEqualTo("HI");
    }

    @Test
    void expandsDerivativePlugins() {
        PluginManager<Greeter> greeters = pluginRegistry.managerFor(Greeter.class);

        assertThat(greeters.ids()).contains("menu:main", "menu:footer");
        assertThat(greeters.has("menu")).isFalse();
        assertThat(greeters.get("menu:main").greet()).isEqualTo("Main menu");
    }

    @Test
    void cachesTheManagerPerType() {
        assertThat(pluginRegistry.managerFor(Greeter.class))
                .isSameAs(pluginRegistry.managerFor(Greeter.class));
    }

    @Test
    void rejectsAnUnknownPluginId() {
        assertThatThrownBy(() -> pluginRegistry.managerFor(Greeter.class).get("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @TestConfiguration
    static class TestPlugins {

        @SpringDropPlugin(id = "english", type = Greeter.class)
        static class EnglishGreeter implements Greeter {
            @Override
            public String greet() {
                return "Hello";
            }
        }

        @SpringDropPlugin(id = "french", type = Greeter.class)
        static class FrenchGreeter implements Greeter {
            @Override
            public String greet() {
                return "Bonjour";
            }
        }

        @SpringDropPlugin(id = "upper", type = Formatter.class)
        static class UpperFormatter implements Formatter {
            @Override
            public String format(String value) {
                return value.toUpperCase();
            }
        }

        @SpringDropPlugin(id = "menu", type = Greeter.class)
        static class MenuGreeter implements Greeter, DerivablePlugin<Greeter> {
            @Override
            public String greet() {
                return "Menu";
            }

            @Override
            public Map<String, Greeter> derivatives() {
                return Map.of(
                        "main", () -> "Main menu",
                        "footer", () -> "Footer menu");
            }
        }
    }
}
