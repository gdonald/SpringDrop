package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.contact.ContactEntityType;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.file.FileAccess;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PrivateFileIntegrationTest.DossierTypes.class)
class PrivateFileIntegrationTest extends AbstractIntegrationTest {

    record Dossier(long id, String label) {
    }

    /** Dossiers only someone holding "view dossiers" may see. */
    static final EntityType DOSSIER = EntityType.content("dossier", Dossier.class)
            .withAccessHandler(ReadersOnly.class);

    private static final String VIEW_DOSSIERS = "view dossiers";

    private static final byte[] MINUTES = "Board minutes, March".getBytes(StandardCharsets.UTF_8);

    private static final long UPLOADER = 52L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private FileAccess access;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @BeforeEach
    void dossierStorage() {
        entityTypeManager.installStorage(DOSSIER.id());
    }

    @AfterEach
    void removeEverything() {
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
        queries.query(DOSSIER.id()).ids().forEach(id -> entities.delete(DOSSIER.id(), id));
    }

    private ManagedFile minutes() throws IOException {
        return files.store(new ByteArrayInputStream(MINUTES), "minutes.txt", MINUTES.length, FileSchemes.PRIVATE,
                UPLOADER);
    }

    private ManagedFile attachedToADossier() throws IOException {
        EntityData dossier = entities.save(EntityData.of(DOSSIER.id(), null, null, "Board", Map.of()));
        ManagedFile file = minutes();
        usage.add(file.id(), "file", DOSSIER.id(), dossier.id());
        return file;
    }

    private MockHttpServletResponse download(ManagedFile file, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(get(files.url(file)).with(who)).andReturn().getResponse();
    }

    private static RequestPostProcessor reader() {
        return user("reader").authorities(new SimpleGrantedAuthority(VIEW_DOSSIERS));
    }

    private static RequestPostProcessor account(long id) {
        return user(new AccountPrincipal(id, "edith", "", true, List.of()));
    }

    @Test
    void aPrivateFileIsServedToSomeoneWhoMayViewWhatUsesIt() throws Exception {
        ManagedFile file = attachedToADossier();

        MockHttpServletResponse response = download(file, reader());

        assertThat(files.url(file)).startsWith(FileService.PRIVATE_URL_PREFIX + "/");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(MINUTES);
        assertThat(response.getContentType()).startsWith("text/plain");
        assertThat(response.getHeader("Cache-Control")).contains("private");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline; filename=\"minutes.txt\"");
    }

    @Test
    void aPrivateFileIsForbiddenToSomeoneWhoMayNotViewWhatUsesIt() throws Exception {
        ManagedFile file = attachedToADossier();

        assertThat(download(file, user("visitor")).getStatus()).isEqualTo(403);
        assertThat(download(file, request -> request).getStatus()).isEqualTo(403);
    }

    @Test
    void aTemporaryFileIsServedOnlyToItsUploader() throws Exception {
        ManagedFile file = minutes();

        assertThat(download(file, account(UPLOADER)).getStatus()).isEqualTo(200);
        assertThat(download(file, account(UPLOADER + 1)).getStatus()).isEqualTo(403);
        assertThat(download(file, reader()).getStatus()).isEqualTo(403);
        assertThat(access.mayDownload(file, null)).isFalse();
    }

    @Test
    void aPathNamingNoStoredPrivateFileIsNotFound() throws Exception {
        assertThat(mockMvc.perform(get(FileService.PRIVATE_URL_PREFIX + "/2026-10/missing.txt").with(reader()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void aPermanentFileWhoseUsersAreGoneOrUnknownIsForbidden() throws Exception {
        ManagedFile file = minutes();
        usage.add(file.id(), "file", "retired_type", 3L);
        usage.add(file.id(), "file", DOSSIER.id(), "draft");
        usage.add(file.id(), "file", DOSSIER.id(), 999_999L);
        usage.add(file.id(), "file", ContactEntityType.FORM_ID, "feedback");

        assertThat(download(file, reader()).getStatus()).isEqualTo(403);
    }

    @TestConfiguration
    static class DossierTypes {

        @Bean
        EntityTypeProvider dossierTypes() {
            return () -> List.of(DOSSIER);
        }

        @Bean
        ReadersOnly readersOnly() {
            return new ReadersOnly();
        }
    }

    static class ReadersOnly implements EntityAccessHandler {

        @Override
        public AccessResult check(EntityType type, Object entity, String operation, Authentication authentication) {
            boolean granted = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(VIEW_DOSSIERS));
            return granted ? AccessResult.allow() : AccessResult.forbid();
        }
    }
}
