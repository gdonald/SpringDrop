package dev.springdrop.kernel.node;

import dev.springdrop.kernel.access.AccessHelper;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.layout.LayoutBuilderDisplay;
import dev.springdrop.kernel.layout.LayoutDisplayManager;
import dev.springdrop.kernel.security.Permissions;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Whether a node may have a layout of its own, and whether the person asking
 * may give it one: its content type's full view mode has to be drawn by Layout
 * Builder and allow it, and the person has to be allowed to edit the node and
 * to configure layout overrides.
 */
@Component
public class NodeLayoutAccess {

    private final LayoutDisplayManager displays;
    private final EntityAccessManager entityAccess;
    private final AccessHelper access;

    public NodeLayoutAccess(LayoutDisplayManager displays, EntityAccessManager entityAccess, AccessHelper access) {
        this.displays = displays;
        this.entityAccess = entityAccess;
        this.access = access;
    }

    /** The content type's full layout, when it lets each node have a layout of its own. */
    public Optional<LayoutBuilderDisplay> overridableDisplay(EntityData node) {
        return displays.enabledDisplay(node, ViewDisplayConfig.FULL_MODE).filter(LayoutBuilderDisplay::allowOverrides);
    }

    public boolean mayOverride(EntityData node) {
        return overridableDisplay(node).isPresent()
                && entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.UPDATE)
                && access.has(Permissions.CONFIGURE_LAYOUT_OVERRIDES);
    }
}
