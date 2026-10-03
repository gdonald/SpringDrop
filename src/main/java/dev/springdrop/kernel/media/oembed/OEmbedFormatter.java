package dev.springdrop.kernel.media.oembed;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import org.springframework.web.util.HtmlUtils;

/**
 * A remote media address as a frame of the embed page, sized 16 to 9 and
 * loaded lazily. An address no provider takes is shown as text.
 */
@SpringDropPlugin(id = OEmbedFormatter.ID, type = FieldFormatter.class)
public class OEmbedFormatter implements FieldFormatter {

    public static final String ID = "oembed";

    private final OEmbedEmbeds embeds;

    public OEmbedFormatter(OEmbedEmbeds embeds) {
        this.embeds = embeds;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        String address = String.valueOf(value).strip();
        if (embeds.client().provider(address).isEmpty()) {
            return HtmlUtils.htmlEscape(address);
        }
        return "<div class=\"ratio ratio-16x9\"><iframe src=\"" + HtmlUtils.htmlEscape(embeds.frameAddress(address))
                + "\" title=\"Remote video\" loading=\"lazy\" allowfullscreen></iframe></div>";
    }
}
