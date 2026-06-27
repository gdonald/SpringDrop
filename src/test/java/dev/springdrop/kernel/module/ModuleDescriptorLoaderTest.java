package dev.springdrop.kernel.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

class ModuleDescriptorLoaderTest {

    private final ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
    private final ModuleDescriptorLoader loader = new ModuleDescriptorLoader(resolver);

    private Resource yamlResource(String yaml) {
        Resource resource = mock(Resource.class);
        try {
            when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(yaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return resource;
    }

    @Test
    void parsesADescriptorWithDependencies() {
        ModuleInfo info = loader.parse(yamlResource("""
                name: blog
                label: Blog
                description: A blog.
                version: "2.0"
                dependencies:
                  - node
                  - text
                """));

        assertThat(info.name()).isEqualTo("blog");
        assertThat(info.version()).isEqualTo("2.0");
        assertThat(info.dependencies()).containsExactly("node", "text");
    }

    @Test
    void defaultsToNoDependenciesWhenAbsent() {
        ModuleInfo info = loader.parse(yamlResource("""
                name: foundation
                label: Foundation
                description: Base.
                version: "1.0"
                """));

        assertThat(info.dependencies()).isEmpty();
    }

    @Test
    void loadsAllDescriptorsFromTheClasspath() throws Exception {
        Resource resource = yamlResource("name: a\nlabel: A\ndescription: A\nversion: \"1\"\n");
        when(resolver.getResources(ModuleDescriptorLoader.LOCATION_PATTERN))
                .thenReturn(new Resource[] {resource});

        assertThat(loader.load()).extracting(ModuleInfo::name).containsExactly("a");
    }

    @Test
    void wrapsAScanFailure() throws Exception {
        when(resolver.getResources(ModuleDescriptorLoader.LOCATION_PATTERN)).thenThrow(new IOException("boom"));

        assertThatThrownBy(loader::load)
                .isInstanceOf(ModuleDependencyException.class)
                .hasMessageContaining("scan");
    }

    @Test
    void wrapsAReadFailure() throws Exception {
        Resource unreadable = mock(Resource.class);
        when(unreadable.getContentAsString(StandardCharsets.UTF_8)).thenThrow(new IOException("nope"));

        assertThatThrownBy(() -> loader.parse(unreadable))
                .isInstanceOf(ModuleDependencyException.class)
                .hasMessageContaining("read module descriptor");
    }
}
