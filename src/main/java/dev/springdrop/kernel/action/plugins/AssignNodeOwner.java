package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.Authentication;

/** Makes the account its settings name the author of a node. A configurable action: the setting is the username. */
@SpringDropPlugin(id = AssignNodeOwner.ID, type = Action.class)
public class AssignNodeOwner implements Action {

    public static final String ID = "node_assign_owner";

    public static final String OWNER = "owner";

    private final NodeService nodes;
    private final EntityAccessManager access;
    private final UserAccountService accounts;

    public AssignNodeOwner(NodeService nodes, EntityAccessManager access, UserAccountService accounts) {
        this.nodes = nodes;
        this.access = access;
        this.accounts = accounts;
    }

    @Override
    public String label() {
        return "Change the author of content";
    }

    @Override
    public String entityType() {
        return NodeEntityType.ID;
    }

    @Override
    public boolean configurable() {
        return true;
    }

    @Override
    public List<FormElement> configurationForm(Map<String, Object> configuration) {
        return List.of(FormElement.of(ElementType.TEXTFIELD, OWNER).label("Username").markRequired()
                .rule(ValidationRule.maxLength(60))
                .value(Objects.toString(configuration.get(OWNER), ""))
                .description("The account made the author of the content the action is applied to."));
    }

    @Override
    public Map<String, String> validate(Map<String, Object> configuration) {
        String owner = Objects.toString(configuration.get(OWNER), "");
        if (owner.isBlank()) {
            return Map.of(OWNER, ValidationRule.REQUIRED_MESSAGE);
        }
        return accounts.findByName(owner).isPresent() ? Map.of()
                : Map.of(OWNER, "There is no account named " + owner + ".");
    }

    @Override
    public AccessResult access(EntityData entity, Authentication who) {
        return access.check(NodeEntityType.ID, entity, EntityAccessHandler.UPDATE, who);
    }

    @Override
    public void execute(EntityData entity, Map<String, Object> configuration, Authentication who) {
        String owner = Objects.toString(configuration.get(OWNER), "");
        UserAccount account = accounts.findByName(owner)
                .orElseThrow(() -> new IllegalStateException("There is no account named " + owner + "."));
        Map<String, Object> fields = new LinkedHashMap<>(entity.fields());
        fields.put(BaseFieldDefinition.OWNER, account.id());
        nodes.save(entity.withFields(fields), Accounts.idOf(who));
    }
}
