package dev.springdrop.kernel.install;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.UploadSchemes;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.media.MediaType;
import dev.springdrop.kernel.media.MediaTypeManager;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.taxonomy.TaxonomyService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DemoProfileTest {

    @Test
    void aSiteThatAlreadyHasImageMediaKeepsItsTypeAndAPictureThatCannotBeStoredIsReported() throws Exception {
        MediaService media = mock(MediaService.class);
        MediaTypeManager mediaTypes = mock(MediaTypeManager.class);
        when(mediaTypes.find("image")).thenReturn(Optional.of(new MediaType("image", "Image", "", "image",
                "field_media_image")));
        TaxonomyService taxonomy = mock(TaxonomyService.class);
        when(taxonomy.save(any())).thenReturn(EntityData.of("taxonomy_term", 4L, "tags", "Baking", Map.of()));
        FileService files = mock(FileService.class);
        when(files.store(any(byte[].class), anyString(), anyString(), anyLong()))
                .thenThrow(new IOException("The disk is full."));
        UploadSchemes schemes = mock(UploadSchemes.class);
        when(schemes.defaultScheme()).thenReturn("public");
        DemoProfile demo = new DemoProfile(mock(StandardProfile.class), taxonomy, mock(NodeService.class), media,
                mediaTypes, files, schemes, mock(MenuLinkContentService.class), mock(ConfigStore.class));

        assertThatThrownBy(demo::install).isInstanceOf(UncheckedIOException.class);
        verify(media, never()).createType(anyString(), anyString(), anyString(), anyString());
    }
}
