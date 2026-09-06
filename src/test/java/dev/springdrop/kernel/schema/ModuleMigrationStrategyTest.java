package dev.springdrop.kernel.schema;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.module.ModuleInfo;
import dev.springdrop.kernel.module.ModuleRegistry;
import java.io.IOException;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.ResourcePatternResolver;

class ModuleMigrationStrategyTest {

    @Test
    void reportsAModuleWhoseMigrationsCannotBeScanned() throws IOException {
        ModuleRegistry registry = mock(ModuleRegistry.class);
        when(registry.installOrder())
                .thenReturn(List.of(new ModuleInfo("blog", "Blog", "", "1.0", List.of())));

        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenThrow(new IOException("classpath unreadable"));

        ModuleMigrationStrategy strategy =
                new ModuleMigrationStrategy(registry, mock(DataSource.class), resolver);

        assertThatThrownBy(() -> strategy.migrate(mock(Flyway.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blog");
    }
}
