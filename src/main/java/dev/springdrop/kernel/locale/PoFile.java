package dev.springdrop.kernel.locale;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and writes gettext {@code .po} files. An entry has an optional
 * {@code msgctxt}, a {@code msgid}, and a {@code msgstr}, or for a plural
 * string a {@code msgid_plural} and {@code msgstr[0]}, {@code msgstr[1]}, and
 * so on, whose forms are joined by {@link Translator#PLURAL_DELIMITER}. A
 * quoted string may continue on the lines after it. The header entry, whose
 * {@code msgid} is empty, is skipped on reading.
 */
public final class PoFile {

    private static final Set<String> SOURCE_KEYWORDS = Set.of("msgctxt", "msgid", "msgid_plural");

    private PoFile() {
    }

    /** One translated string read from a file. */
    public record Entry(String context, String source, String translation) {
    }

    /** A file the reader cannot make sense of, naming the line where it went wrong. */
    public static class MalformedException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public MalformedException(int line, String problem) {
            super("Line " + line + ": " + problem);
        }
    }

    public static String write(String langcode, String languageName, List<TranslatableString> strings) {
        StringBuilder po = new StringBuilder("# ").append(languageName).append(" translation of SpringDrop.\n")
                .append("msgid \"\"\nmsgstr \"\"\n")
                .append("\"Content-Type: text/plain; charset=UTF-8\\n\"\n")
                .append("\"Language: ").append(escape(langcode)).append("\\n\"\n")
                .append("\"Plural-Forms: nplurals=2; plural=(n != 1);\\n\"\n");
        for (TranslatableString string : strings) {
            po.append('\n');
            if (!string.context().isEmpty()) {
                po.append("msgctxt \"").append(escape(string.context())).append("\"\n");
            }
            String[] sources = string.source().split(Translator.PLURAL_DELIMITER, -1);
            po.append("msgid \"").append(escape(sources[0])).append("\"\n");
            if (sources.length == 1) {
                po.append("msgstr \"").append(escape(string.translation())).append("\"\n");
                continue;
            }
            po.append("msgid_plural \"").append(escape(sources[1])).append("\"\n");
            String[] forms = string.translation().split(Translator.PLURAL_DELIMITER, -1);
            for (int index = 0; index < forms.length; index++) {
                po.append("msgstr[").append(index).append("] \"").append(escape(forms[index])).append("\"\n");
            }
        }
        return po.toString();
    }

    public static List<Entry> read(String text) {
        List<Entry> entries = new ArrayList<>();
        Map<String, StringBuilder> fields = new LinkedHashMap<>();
        StringBuilder last = null;
        String[] lines = text.split("\r?\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            int number = index + 1;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("\"")) {
                if (last == null) {
                    throw new MalformedException(number, "a quoted string with no keyword before it");
                }
                last.append(unquote(number, line));
                continue;
            }
            int space = line.indexOf(' ');
            if (space < 0) {
                throw new MalformedException(number, "expected a keyword and a quoted string");
            }
            String keyword = line.substring(0, space);
            if (keyword.equals("msgstr")) {
                keyword = "msgstr[0]";
            }
            if (!SOURCE_KEYWORDS.contains(keyword) && !keyword.matches("msgstr\\[\\d]")) {
                throw new MalformedException(number, "unknown keyword " + keyword);
            }
            if ((keyword.equals("msgctxt") || keyword.equals("msgid")) && hasTranslation(fields)) {
                entry(fields).ifPresent(entries::add);
                fields.clear();
            }
            last = new StringBuilder(unquote(number, line.substring(space + 1).strip()));
            fields.put(keyword, last);
        }
        entry(fields).ifPresent(entries::add);
        return entries;
    }

    private static boolean hasTranslation(Map<String, StringBuilder> fields) {
        return fields.containsKey("msgstr[0]");
    }

    /**
     * The entry the fields make, or nothing for the header, an entry with no
     * translation, or one whose every form is empty.
     */
    private static Optional<Entry> entry(Map<String, StringBuilder> fields) {
        String source = text(fields, "msgid");
        List<String> forms = new ArrayList<>();
        for (int form = 0; fields.containsKey("msgstr[" + form + "]"); form++) {
            forms.add(text(fields, "msgstr[" + form + "]"));
        }
        if (source.isEmpty() || forms.stream().allMatch(String::isEmpty)) {
            return Optional.empty();
        }
        String plural = text(fields, "msgid_plural");
        return Optional.of(new Entry(text(fields, "msgctxt"),
                plural.isEmpty() ? source : source + Translator.PLURAL_DELIMITER + plural,
                String.join(Translator.PLURAL_DELIMITER, forms)));
    }

    private static String text(Map<String, StringBuilder> fields, String keyword) {
        StringBuilder field = fields.get(keyword);
        return field == null ? "" : field.toString();
    }

    private static String unquote(int line, String quoted) {
        if (quoted.length() < 2 || !quoted.startsWith("\"") || !quoted.endsWith("\"")) {
            throw new MalformedException(line, "expected a quoted string");
        }
        String inner = quoted.substring(1, quoted.length() - 1);
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < inner.length(); index++) {
            char character = inner.charAt(index);
            if (character != '\\') {
                text.append(character);
                continue;
            }
            index++;
            if (index == inner.length()) {
                throw new MalformedException(line, "a backslash at the end of a string");
            }
            char escaped = inner.charAt(index);
            text.append(switch (escaped) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case '"', '\\' -> escaped;
                default -> throw new MalformedException(line, "an unknown escape \\" + escaped);
            });
        }
        return text.toString();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
    }
}
