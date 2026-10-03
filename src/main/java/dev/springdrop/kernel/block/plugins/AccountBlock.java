package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.Renderable;
import java.util.Map;
import java.util.Optional;

/** The account signed in, drawn as a placeholder the {@link AccountGreeting} fills for each request. */
@SpringDropPlugin(id = AccountBlock.ID, type = BlockPlugin.class)
public class AccountBlock implements BlockPlugin {

    public static final String ID = "user_account_block";

    @Override
    public String label() {
        return "Account";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return Optional.of(Renderable.placeholder(AccountGreeting.ID, Map.of()));
    }
}
