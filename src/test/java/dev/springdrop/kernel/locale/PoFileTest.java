package dev.springdrop.kernel.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PoFileTest {

    private static final String D = Translator.PLURAL_DELIMITER;

    @Test
    void aWrittenFileReadsBackAsTheSameTranslations() {
        List<TranslatableString> strings = List.of(
                new TranslatableString(1, "Read more", "", "Lire la suite"),
                new TranslatableString(2, "May", "Long month name", "Mai"),
                new TranslatableString(3, "1 comment" + D + "@count comments", "", "1 commentaire" + D
                        + "@count commentaires"),
                new TranslatableString(4, "Say \"hi\"\tthen\nback\\slash", "", "Dis \"salut\""));

        String po = PoFile.write("fr", "French", strings);

        assertThat(po).startsWith("# French translation of SpringDrop.\nmsgid \"\"\nmsgstr \"\"\n")
                .contains("\"Language: fr\\n\"")
                .contains("msgctxt \"Long month name\"\nmsgid \"May\"\nmsgstr \"Mai\"")
                .contains("msgid \"1 comment\"\nmsgid_plural \"@count comments\"\nmsgstr[0] \"1 commentaire\"\n"
                        + "msgstr[1] \"@count commentaires\"")
                .contains("msgid \"Say \\\"hi\\\"\\tthen\\nback\\\\slash\"");
        assertThat(PoFile.read(po)).containsExactly(
                new PoFile.Entry("", "Read more", "Lire la suite"),
                new PoFile.Entry("Long month name", "May", "Mai"),
                new PoFile.Entry("", "1 comment" + D + "@count comments", "1 commentaire" + D
                        + "@count commentaires"),
                new PoFile.Entry("", "Say \"hi\"\tthen\nback\\slash", "Dis \"salut\""));
    }

    @Test
    void aStringContinuesOnTheLinesAfterItAndCommentsAndUntranslatedEntriesAreSkipped() {
        String po = """
                # translator comment
                #, fuzzy
                msgctxt "menu"
                msgid ""
                "Main "
                "navigation"
                msgstr ""
                "Navigation "
                "principale"

                msgid "Untranslated"
                msgstr ""

                msgid "Empty plural"
                msgid_plural "Empty plurals"
                msgstr[0] ""
                msgstr[1] ""
                """;

        assertThat(PoFile.read(po)).containsExactly(new PoFile.Entry("menu", "Main navigation",
                "Navigation principale"));
    }

    @Test
    void aFileItCannotReadNamesTheLine() {
        assertThatThrownBy(() -> PoFile.read("msgid"))
                .isInstanceOf(PoFile.MalformedException.class)
                .hasMessage("Line 1: expected a keyword and a quoted string");
        assertThatThrownBy(() -> PoFile.read("msgid \"a\"\nmsgfoo \"b\""))
                .hasMessage("Line 2: unknown keyword msgfoo");
        assertThatThrownBy(() -> PoFile.read("\"orphan\""))
                .hasMessage("Line 1: a quoted string with no keyword before it");
        assertThatThrownBy(() -> PoFile.read("msgid unquoted"))
                .hasMessage("Line 1: expected a quoted string");
        assertThatThrownBy(() -> PoFile.read("msgid \"\"\"x"))
                .hasMessage("Line 1: expected a quoted string");
        assertThatThrownBy(() -> PoFile.read("msgid \"x"))
                .hasMessage("Line 1: expected a quoted string");
        assertThatThrownBy(() -> PoFile.read("msgid \""))
                .hasMessage("Line 1: expected a quoted string");
        assertThat(PoFile.read("msgid \"\"")).isEmpty();
        assertThatThrownBy(() -> PoFile.read("msgid \"end\\\""))
                .hasMessage("Line 1: a backslash at the end of a string");
        assertThatThrownBy(() -> PoFile.read("msgid \"bad \\q\""))
                .hasMessage("Line 1: an unknown escape \\q");
    }

    @Test
    void windowsLineEndingsAreRead() {
        assertThat(PoFile.read("msgid \"Yes\"\r\nmsgstr \"Oui\"\r\n"))
                .containsExactly(new PoFile.Entry("", "Yes", "Oui"));
    }
}
