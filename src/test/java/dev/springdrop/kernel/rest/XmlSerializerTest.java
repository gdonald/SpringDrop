package dev.springdrop.kernel.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;

class XmlSerializerTest {

    private final XmlSerializer xml = new XmlSerializer();

    @Test
    void listsAndMapsReadBackAsTheyWereWritten() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tags", List.of("4", "7"));
        fields.put("link", Map.of("uri", "/about", "title", "About <us>"));
        EntityRepresentation entity = new EntityRepresentation("node", 4L, null, "article", null, "en", fields);

        String written = xml.serialize(entity);

        assertThat(written).doesNotContain("uuid=").doesNotContain("<label>").contains("About &lt;us&gt;");
        EntityRepresentation read = xml.deserialize(written);
        assertThat(read.fields()).containsEntry("tags", List.of("4", "7"))
                .containsEntry("link", Map.of("uri", "/about", "title", "About <us>"));
        assertThat(read.id()).isEqualTo("4");
        assertThat(read.uuid()).isNull();
        assertThat(read.label()).isNull();
        assertThat(xml.mediaType()).isEqualTo("application/xml");
    }

    @Test
    void aBodyThatIsNotAnEntityOrDeclaresADocumentTypeIsRefused() {
        assertThatThrownBy(() -> xml.deserialize("<node/>")).isInstanceOf(RestSerializer.MalformedBodyException.class);
        assertThatThrownBy(() -> xml.deserialize("<entity>")).isInstanceOf(RestSerializer.MalformedBodyException.class);
        assertThatThrownBy(() -> xml.deserialize("<!DOCTYPE entity [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<entity><label>&x;</label></entity>")).isInstanceOf(RestSerializer.MalformedBodyException.class);
        assertThat(xml.deserialize("<entity><other/></entity>").fields()).isEmpty();
    }

    @Test
    void aParserThatCannotRefuseDocumentTypesStopsTheRead() {
        XmlSerializer unsafe = new XmlSerializer(new DocumentBuilderFactory() {
            @Override
            public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
                throw new ParserConfigurationException("unsupported");
            }

            @Override
            public void setAttribute(String name, Object value) {
            }

            @Override
            public Object getAttribute(String name) {
                return null;
            }

            @Override
            public void setFeature(String name, boolean value) {
            }

            @Override
            public boolean getFeature(String name) {
                return false;
            }
        });

        assertThatThrownBy(() -> unsafe.deserialize("<entity/>")).isInstanceOf(IllegalStateException.class);
    }
}
