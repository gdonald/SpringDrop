package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import org.springframework.stereotype.Component;

/**
 * Who wrote a comment, as it is shown: the account's name, or the name someone
 * not signed in left, marked as not verified, or Anonymous when there is
 * neither.
 */
@Component
public class CommentAuthors {

    private final UserAccountService accounts;

    public CommentAuthors(UserAccountService accounts) {
        this.accounts = accounts;
    }

    public String nameOf(EntityData comment) {
        Object owner = comment.fields().get(BaseFieldDefinition.OWNER);
        if (owner instanceof Number account && account.longValue() != UserAccount.ANONYMOUS_ID) {
            return accounts.find(account.longValue()).map(UserAccount::name).orElse(UserAccount.ANONYMOUS_NAME);
        }
        String name = String.valueOf(comment.fields().getOrDefault(CommentEntityType.NAME, "")).strip();
        return name.isEmpty() ? UserAccount.ANONYMOUS_NAME : name + " (not verified)";
    }
}
