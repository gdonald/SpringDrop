package dev.springdrop.kernel.install;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.config.sync.ConfigDependencyCalculator;
import dev.springdrop.kernel.config.sync.ConfigFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.ResourcePatternResolver;

class ProfileConfigTest {

    @Test
    void aProfileWhoseFilesCannotBeListedIsReported() throws Exception {
        ResourcePatternResolver resources = mock(ResourcePatternResolver.class);
        when(resources.getResources(anyString())).thenThrow(new IOException("The class path cannot be read."));
        ProfileConfig config = new ProfileConfig(resources, new ConfigFiles(), mock(ConfigDependencyCalculator.class));

        assertThatThrownBy(() -> config.objects(new MinimalProfile())).isInstanceOf(UncheckedIOException.class);
    }
}
