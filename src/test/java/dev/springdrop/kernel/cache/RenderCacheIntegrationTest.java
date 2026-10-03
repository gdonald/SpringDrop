package dev.springdrop.kernel.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.render.Attachments;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
class RenderCacheIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "cache_page";

    @Autowired
    private RenderService renderer;

    @Autowired
    private RenderCache renderCache;

    @Autowired
    private CacheTagInvalidator invalidator;

    @Autowired
    private CacheContexts contexts;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private ApplicationEventPublisher events;

    @BeforeEach
    void emptyCache() {
        renderCache.clear();
        nodeTypes.save(NodeType.of(PAGE, "Page"));
    }

    @AfterEach
    void removeEverything() {
        renderCache.clear();
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        nodeTypes.delete(PAGE);
        SecurityContextHolder.clearContext();
    }

    private EntityData node(String title) {
        return nodes.save(EntityData.of(NodeEntityType.ID, null, PAGE, title, Map.of()), 1L);
    }

    private static Renderable teaser(EntityData node, String text) {
        return Renderable.of("markup").with("value", text).cacheTag(CacheTags.entity(NodeEntityType.ID, node.id()))
                .cacheKeys("teaser", String.valueOf(node.id()));
    }

    private String drawn(Renderable renderable) {
        return renderer.render(renderable).html();
    }

    @Test
    void editingANodeInvalidatesExactlyTheEntriesTaggedWithIt() {
        EntityData first = node("First");
        EntityData second = node("Second");
        drawn(teaser(first, "first, as kept"));
        drawn(teaser(second, "second, as kept"));
        assertThat(drawn(teaser(first, "first, drawn again"))).contains("first, as kept");

        nodes.save(first, 1L);

        assertThat(drawn(teaser(first, "first, drawn again"))).contains("first, drawn again");
        assertThat(drawn(teaser(second, "second, drawn again"))).contains("second, as kept");
    }

    @Test
    void aListTagIsInvalidatedWhenAnyEntityOfTheTypeChanges() {
        Renderable listing = Renderable.of("markup").with("value", "listing, as kept")
                .cacheTag(CacheTags.list(NodeEntityType.ID)).cacheKeys("listing");
        drawn(listing);

        EntityData added = node("Added");
        assertThat(drawn(listing.with("value", "listing, drawn again"))).contains("listing, drawn again");
        nodes.delete(((Number) added.id()).longValue());
        assertThat(drawn(listing.with("value", "listing, after delete"))).contains("listing, after delete");
    }

    @Test
    void aConfigTagIsInvalidatedWhenTheConfigIsSaved() {
        Renderable branding = Renderable.of("markup").with("value", "branding, as kept")
                .cacheTag(CacheTags.config("system.site")).cacheKeys("branding");
        drawn(branding);

        configStore.save("system.site", Map.of("name", "Library"));

        assertThat(drawn(branding.with("value", "branding, drawn again"))).contains("branding, drawn again");
    }

    @Test
    void anEntryVariesByItsContextsIncludingThoseInsideIt() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET",
                "/news")));
        Renderable byRole = Renderable.of("container").cacheKeys("greeting")
                .child(Renderable.of("markup").with("value", "for editors").cacheContext(CacheContexts.USER_ROLES));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("edith", null,
                List.of(new SimpleGrantedAuthority("ROLE_editor"))));
        String editors = drawn(byRole);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("frank", null,
                List.of(new SimpleGrantedAuthority("ROLE_writer"))));
        String writers = drawn(Renderable.of("container").cacheKeys("greeting")
                .child(Renderable.of("markup").with("value", "for writers").cacheContext(CacheContexts.USER_ROLES)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("edith", null,
                List.of(new SimpleGrantedAuthority("ROLE_editor"))));

        assertThat(editors).contains("for editors");
        assertThat(writers).contains("for writers");
        assertThat(drawn(Renderable.of("container").cacheKeys("greeting")
                .child(Renderable.of("markup").with("value", "changed").cacheContext(CacheContexts.USER_ROLES))))
                .contains("for editors");
    }

    @Test
    void aKeptEntryBringsItsMetadataAndAttachmentsToThePage() {
        Renderable styled = Renderable.of("markup").with("value", "styled").cacheTag("styled:1")
                .styleSheet("/css/styled.css").cacheKeys("styled");
        drawn(styled);

        var page = renderer.render(Renderable.of("container").child(styled));

        assertThat(page.cache().tags()).contains("styled:1");
        assertThat(page.attachments().styleSheets()).contains("/css/styled.css");
    }

    @Test
    void outputThatMayNotBeCachedOrHoldsAPlaceholderIsNotKept() {
        drawn(Renderable.of("markup").with("value", "uncached").maxAge(Duration.ZERO).cacheKeys("uncached"));
        drawn(Renderable.of("container").cacheKeys("lazy")
                .child(Renderable.lazy(() -> Renderable.of("markup").with("value", "built late"))));

        assertThat(drawn(Renderable.of("markup").with("value", "again").maxAge(Duration.ZERO)
                .cacheKeys("uncached"))).contains("again");
        assertThat(drawn(Renderable.of("container").cacheKeys("lazy")
                .child(Renderable.lazy(() -> Renderable.of("markup").with("value", "built later")))))
                .contains("built later");
    }

    @Test
    void anEntryIsUsedUntilItsMaxAgePasses() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(RenderCache.CACHE_NAME);
        Instant now = Instant.parse("2026-10-03T12:00:00Z");
        RenderCache early = new RenderCache(manager, invalidator, contexts, Clock.fixed(now, ZoneOffset.UTC));
        RenderCache later = new RenderCache(manager, invalidator, contexts, Clock.fixed(now.plusSeconds(61),
                ZoneOffset.UTC));
        CacheMetadata minute = CacheMetadata.EMPTY.withMaxAge(Duration.ofMinutes(1));

        early.put(List.of("clock"), Set.of(), new RenderCache.Cached("noon", minute, Attachments.NONE));

        assertThat(early.get(List.of("clock"), Set.of())).isPresent();
        assertThat(later.get(List.of("clock"), Set.of())).isEmpty();
        assertThat(early.get(List.of("clock"), Set.of())).isEmpty();
    }

    @Test
    void eventsThatChangeNothingInvalidateNothing() {
        EntityData kept = node("Kept");
        long before = invalidator.checksum(List.of(CacheTags.entity(NodeEntityType.ID, kept.id())));

        events.publishEvent(new EntityEvent(kept, NodeEntityType.ID, EntityEvent.Phase.LOAD));
        events.publishEvent(new EntityEvent("not an entity", NodeEntityType.ID, EntityEvent.Phase.UPDATE));

        assertThat(invalidator.checksum(List.of(CacheTags.entity(NodeEntityType.ID, kept.id())))).isEqualTo(before);
    }
}
