package dev.springdrop.kernel.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

class PermissionFileLoaderTest {

    private final ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);

    private final PermissionFileLoader loader = new PermissionFileLoader(resolver);

    private Resource declaring(String yaml, String filename) throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.getContentAsString(any(Charset.class))).thenReturn(yaml);
        when(resource.getFilename()).thenReturn(filename);
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {resource});
        return resource;
    }

    @Test
    void anEntryWithNoDetailsIsNamedAfterItself() throws IOException {
        declaring("mind the shop:\n", "shop.permissions.yml");

        assertThat(loader.permissions()).singleElement().satisfies(permission -> {
            assertThat(permission.name()).isEqualTo("mind the shop");
            assertThat(permission.title()).isEqualTo("mind the shop");
            assertThat(permission.description()).isEmpty();
            assertThat(permission.restricted()).isFalse();
            assertThat(permission.provider()).isEqualTo("shop");
        });
    }

    @Test
    void aFileWithNothingInItDeclaresNothing() throws IOException {
        declaring("", "empty.permissions.yml");

        assertThat(loader.permissions()).isEmpty();
    }

    @Test
    void aFileWithNoNameToGoByHasNoProvider() throws IOException {
        declaring("mind the shop:\n  title: Mind the shop\n", null);

        assertThat(loader.permissions()).singleElement()
                .satisfies(permission -> assertThat(permission.provider()).isEmpty());
    }

    @Test
    void aClasspathThatCannotBeScannedIsReported() throws IOException {
        when(resolver.getResources(anyString())).thenThrow(new IOException("classpath unreadable"));

        assertThatThrownBy(loader::permissions)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scan for permission declarations");
    }

    @Test
    void aFileThatCannotBeReadIsReported() throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.getContentAsString(any(Charset.class))).thenThrow(new IOException("unreadable"));
        when(resource.getDescription()).thenReturn("broken.permissions.yml");
        when(resolver.getResources(anyString())).thenReturn(new Resource[] {resource});

        assertThatThrownBy(loader::permissions)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broken.permissions.yml");
    }
}
