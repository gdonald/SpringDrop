package dev.springdrop.kernel.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.node.NodePermissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class TermAccessHandlerTest {

    private static final EntityType TERM = TaxonomyEntityType.definition();

    private static final String TAGS = "tags";

    private final TermAccessHandler handler = new TermAccessHandler();

    private static Authentication holding(String... permissions) {
        return UsernamePasswordAuthenticationToken.authenticated("someone", null,
                List.of(permissions).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static EntityData term(boolean published) {
        return EntityData.of(TaxonomyEntityType.ID, 4L, TAGS, "Weather",
                Map.of(BaseFieldDefinition.STATUS, published));
    }

    private boolean allowed(Object entity, String operation, Authentication who) {
        return handler.check(TERM, entity, operation, who).allowed();
    }

    @Test
    void aPublishedTermIsOpenToAnyoneWhoMayAccessContent() {
        assertThat(allowed(term(true), EntityAccessHandler.VIEW, holding(NodePermissions.ACCESS_CONTENT))).isTrue();
        assertThat(allowed(term(true), EntityAccessHandler.VIEW, holding())).isFalse();
    }

    @Test
    void anUnpublishedTermIsClosedToAnyoneWhoMayOnlyAccessContent() {
        assertThat(allowed(term(false), EntityAccessHandler.VIEW, holding(NodePermissions.ACCESS_CONTENT))).isFalse();
    }

    @Test
    void editingAndDeletingGoByTheTermsVocabulary() {
        assertThat(allowed(term(true), EntityAccessHandler.UPDATE, holding(TaxonomyPermissions.edit(TAGS)))).isTrue();
        assertThat(allowed(term(true), EntityAccessHandler.UPDATE, holding(TaxonomyPermissions.edit("topics"))))
                .isFalse();
        assertThat(allowed(term(true), EntityAccessHandler.DELETE, holding(TaxonomyPermissions.delete(TAGS))))
                .isTrue();
    }

    @Test
    void creatingIsAskedAboutAVocabulary() {
        assertThat(allowed(TAGS, EntityAccessHandler.CREATE, holding(TaxonomyPermissions.create(TAGS)))).isTrue();
        assertThat(allowed(term(true), EntityAccessHandler.CREATE, holding(TaxonomyPermissions.create(TAGS))))
                .isFalse();
    }

    @Test
    void administeringTaxonomyAllowsEverything() {
        assertThat(allowed(term(false), "anything", holding(TaxonomyPermissions.ADMINISTER_TAXONOMY))).isTrue();
    }

    @Test
    void anOperationTermsDoNotHaveOrSomethingOtherThanATermSaysNothing() {
        assertThat(handler.check(TERM, term(true), "publish", holding(NodePermissions.ACCESS_CONTENT))
                .neutralDecision()).isTrue();
        assertThat(handler.check(TERM, "not a term", EntityAccessHandler.VIEW, holding(NodePermissions.ACCESS_CONTENT))
                .neutralDecision()).isTrue();
        assertThat(handler.check(TERM, term(true), EntityAccessHandler.VIEW, null).neutralDecision()).isTrue();
    }
}
