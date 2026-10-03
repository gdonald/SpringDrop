package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Arrays;
import java.util.Map;

/**
 * Whether an entity takes comments: open, closed, or hidden. An entity that
 * has never said starts with the field's default value, or open when it has
 * none.
 */
@SpringDropPlugin(id = CommentStatusWidget.ID, type = FieldWidget.class)
public class CommentStatusWidget implements FieldWidget {

    public static final String ID = "comment_default";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        Object shown = (value != null) ? value : context.instance().defaultValue();
        CommentStatus status = (shown == null) ? CommentStatus.OPEN : CommentStatus.of(shown);
        return FormElement.of(ElementType.RADIOS, context.elementName(delta))
                .label(context.label())
                .description(context.instance().description())
                .value(String.valueOf(status.value()))
                .options(Arrays.stream(CommentStatus.values())
                        .map(option -> new SelectOption(String.valueOf(option.value()), option.label()))
                        .toList());
    }

    /** The status chosen, which a submission naming none of them leaves out. */
    @Override
    public Object extract(WidgetContext context, int delta, Map<String, Object> submitted) {
        Object chosen = submitted.get(context.elementName(delta));
        return (chosen != null && String.valueOf(chosen).matches("[012]"))
                ? Integer.valueOf(String.valueOf(chosen))
                : null;
    }
}
