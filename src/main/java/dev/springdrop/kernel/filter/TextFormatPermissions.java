package dev.springdrop.kernel.filter;

import dev.springdrop.kernel.filter.filters.HtmlRestrictorFilter;
import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * One permission per text format other than the fallback, built from the
 * formats the site has. A format without the allowed-HTML filter lets its
 * writers put any markup on the page, so its permission is restricted.
 */
@Component
public class TextFormatPermissions implements PermissionProvider {

    public static final String PROVIDER = "filter";

    private final TextFormatManager formats;

    public TextFormatPermissions(TextFormatManager formats) {
        this.formats = formats;
    }

    @Override
    public List<PermissionDefinition> permissions() {
        return formats.all().stream()
                .filter(format -> !format.fallback())
                .map(format -> {
                    PermissionDefinition permission = PermissionDefinition.of(
                            TextFormatManager.permission(format.id()), "Use the " + format.label() + " text format",
                            PROVIDER);
                    return format.filter(HtmlRestrictorFilter.ID).isPresent()
                            ? permission : permission.asRestricted();
                })
                .toList();
    }
}
