package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * A file as a link to it, shown by its description or, when it has none, by
 * its name, with its kind and size beside it. A value marked not to be listed,
 * or pointing at a file that is gone, shows nothing.
 */
@SpringDropPlugin(id = FileFormatter.ID, type = FieldFormatter.class)
public class FileFormatter implements FieldFormatter {

    public static final String ID = "file_default";

    private final FileService files;

    public FileFormatter(FileService files) {
        this.files = files;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        if (FileItem.part(value, FileItem.DISPLAY).equals("false")) {
            return "";
        }
        return FileItem.fileId(value).flatMap(files::find).map(file -> {
            String description = FileItem.part(value, FileItem.DESCRIPTION);
            String shown = description.isBlank() ? file.filename() : description;
            return "<span class=\"file\"><a href=\"" + HtmlUtils.htmlEscape(files.url(file)) + "\" type=\""
                    + HtmlUtils.htmlEscape(file.mime()) + "\">" + HtmlUtils.htmlEscape(shown) + "</a>"
                    + " <span class=\"text-body-secondary\">(" + readableSize(file.size()) + ")</span></span>";
        }).orElse("");
    }

    /** A size in bytes as people read it: bytes, KB, or MB, at most one decimal place. */
    public static String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) {
            return oneDecimal(kilobytes) + " KB";
        }
        return oneDecimal(kilobytes / 1024.0) + " MB";
    }

    private static String oneDecimal(double amount) {
        String text = String.format(Locale.ROOT, "%.1f", amount);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
