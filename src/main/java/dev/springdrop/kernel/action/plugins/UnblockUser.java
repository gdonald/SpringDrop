package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.user.UserAccountService;
import java.util.Map;
import org.springframework.security.core.Authentication;

@SpringDropPlugin(id = UnblockUser.ID, type = Action.class)
public class UnblockUser extends UserStatusAction {

    public static final String ID = "user_unblock";

    private final UserAccountService accounts;

    public UnblockUser(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public String label() {
        return "Unblock account";
    }

    @Override
    public void execute(EntityData entity, Map<String, Object> configuration, Authentication who) {
        accounts.unblock(((Number) entity.id()).longValue());
    }
}
