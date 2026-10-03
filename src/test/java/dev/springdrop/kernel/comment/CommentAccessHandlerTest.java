package dev.springdrop.kernel.comment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.user.AccountPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CommentAccessHandlerTest {

    private static final EntityType COMMENT = CommentEntityType.definition();

    private static final long AUTHOR = 7L;

    private final CommentAccessHandler handler = new CommentAccessHandler();

    private static Authentication account(long id, String... permissions) {
        AccountPrincipal principal = new AccountPrincipal(id, "account" + id, "", true, List.of(permissions));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static EntityData comment(boolean published) {
        return EntityData.of(CommentEntityType.ID, 3L, null, "Nice",
                Map.of(BaseFieldDefinition.STATUS, published, BaseFieldDefinition.OWNER, AUTHOR));
    }

    private boolean allowed(Object entity, String operation, Authentication who) {
        return handler.check(COMMENT, entity, operation, who).allowed();
    }

    @Test
    void aPublishedCommentIsOpenToAnyoneWhoMayAccessComments() {
        assertThat(allowed(comment(true), EntityAccessHandler.VIEW,
                account(8L, CommentPermissions.ACCESS_COMMENTS))).isTrue();
        assertThat(allowed(comment(false), EntityAccessHandler.VIEW,
                account(8L, CommentPermissions.ACCESS_COMMENTS))).isFalse();
        assertThat(allowed(comment(true), EntityAccessHandler.VIEW, account(8L))).isFalse();
    }

    @Test
    void itsAuthorMayChangeItWhileHoldingEditOwnComments() {
        assertThat(allowed(comment(true), EntityAccessHandler.UPDATE,
                account(AUTHOR, CommentPermissions.EDIT_OWN))).isTrue();
        assertThat(allowed(comment(true), EntityAccessHandler.UPDATE,
                account(8L, CommentPermissions.EDIT_OWN))).isFalse();
        assertThat(allowed(comment(true), EntityAccessHandler.UPDATE, account(AUTHOR))).isFalse();
    }

    @Test
    void someoneWhoIsNotASiteAccountOwnsNoComment() {
        Authentication outsider = UsernamePasswordAuthenticationToken.authenticated("service", null,
                List.of(new SimpleGrantedAuthority(CommentPermissions.EDIT_OWN)));

        assertThat(allowed(comment(true), EntityAccessHandler.UPDATE, outsider)).isFalse();
        assertThat(allowed(EntityData.of(CommentEntityType.ID, 3L, null, "Ownerless", Map.of()),
                EntityAccessHandler.UPDATE, account(AUTHOR, CommentPermissions.EDIT_OWN))).isFalse();
    }

    @Test
    void creatingTakesPostComments() {
        assertThat(allowed(CommentEntityType.ID, EntityAccessHandler.CREATE,
                account(8L, CommentPermissions.POST_COMMENTS))).isTrue();
        assertThat(allowed(CommentEntityType.ID, EntityAccessHandler.CREATE, account(8L))).isFalse();
    }

    @Test
    void administeringCommentsAllowsEverythingIncludingDeleting() {
        assertThat(allowed(comment(false), EntityAccessHandler.DELETE,
                account(8L, CommentPermissions.ADMINISTER_COMMENTS))).isTrue();
        assertThat(allowed(comment(true), EntityAccessHandler.DELETE,
                account(AUTHOR, CommentPermissions.EDIT_OWN))).isFalse();
    }

    @Test
    void somethingOtherThanACommentOrNoOneSaysNothing() {
        assertThat(handler.check(COMMENT, "not a comment", EntityAccessHandler.VIEW,
                account(8L, CommentPermissions.ACCESS_COMMENTS)).neutralDecision()).isTrue();
        assertThat(handler.check(COMMENT, comment(true), EntityAccessHandler.VIEW, null).neutralDecision()).isTrue();
        assertThat(handler.check(COMMENT, comment(true), EntityAccessHandler.UPDATE, null).neutralDecision()).isTrue();
    }
}
