package dev.springdrop.kernel.rest;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Entities as XML. The entity's keys are attributes of {@code <entity>}, its
 * label is {@code <label>}, and each field is a {@code <field name="...">}
 * holding one {@code <value>} per value. A value that is a map holds an
 * {@code <entry key="...">} per key. Values read back as text, and a request
 * body's document type declarations are refused, so a body cannot reach
 * files or other hosts.
 */
@SpringDropPlugin(id = XmlSerializer.ID, type = RestSerializer.class)
public class XmlSerializer implements RestSerializer {

    public static final String ID = "xml";

    private final DocumentBuilderFactory parsers;

    public XmlSerializer() {
        this(DocumentBuilderFactory.newInstance());
    }

    XmlSerializer(DocumentBuilderFactory parsers) {
        this.parsers = parsers;
    }

    @Override
    public String mediaType() {
        return "application/xml";
    }

    @Override
    public String serialize(EntityRepresentation entity) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><entity");
        attribute(xml, "type", entity.type());
        attribute(xml, "id", entity.id());
        attribute(xml, "uuid", entity.uuid());
        attribute(xml, "bundle", entity.bundle());
        attribute(xml, "langcode", entity.langcode());
        xml.append('>');
        if (entity.label() != null) {
            xml.append("<label>").append(escape(entity.label())).append("</label>");
        }
        xml.append("<fields>");
        entity.fields().forEach((name, value) -> {
            xml.append("<field name=\"").append(escape(name)).append("\">");
            List<?> values = value instanceof List<?> many ? many : List.of(value);
            values.forEach(each -> appendValue(xml, each));
            xml.append("</field>");
        });
        return xml.append("</fields></entity>").toString();
    }

    @Override
    public EntityRepresentation deserialize(String body) {
        Element root;
        try {
            root = parser().parse(new InputSource(new StringReader(body))).getDocumentElement();
        } catch (SAXException | IOException unreadable) {
            throw new MalformedBodyException("The body is not an XML entity.");
        }
        if (!root.getTagName().equals("entity")) {
            throw new MalformedBodyException("The body is not an XML entity.");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        String label = null;
        for (Element child : children(root)) {
            if (child.getTagName().equals("label")) {
                label = child.getTextContent();
            } else if (child.getTagName().equals("fields")) {
                for (Element field : children(child)) {
                    List<Object> values = new ArrayList<>();
                    children(field).forEach(value -> values.add(readValue(value)));
                    fields.put(field.getAttribute("name"), values.size() == 1 ? values.getFirst() : values);
                }
            }
        }
        return new EntityRepresentation(blankToNull(root.getAttribute("type")), blankToNull(root.getAttribute("id")),
                blankToNull(root.getAttribute("uuid")), blankToNull(root.getAttribute("bundle")), label,
                blankToNull(root.getAttribute("langcode")), fields);
    }

    private static void appendValue(StringBuilder xml, Object value) {
        xml.append("<value>");
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, entryValue) -> xml.append("<entry key=\"").append(escape(String.valueOf(key)))
                    .append("\">").append(escape(String.valueOf(entryValue))).append("</entry>"));
        } else {
            xml.append(escape(String.valueOf(value)));
        }
        xml.append("</value>");
    }

    private static Object readValue(Element value) {
        List<Element> entries = children(value);
        if (entries.isEmpty()) {
            return value.getTextContent();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        entries.forEach(entry -> map.put(entry.getAttribute("key"), entry.getTextContent()));
        return map;
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static void attribute(StringBuilder xml, String name, Object value) {
        if (value != null) {
            xml.append(' ').append(name).append("=\"").append(escape(String.valueOf(value))).append('"');
        }
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /** A parser refusing document type declarations, so a body cannot define entities or reach outside files. */
    private DocumentBuilder parser() {
        try {
            parsers.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            parsers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return parsers.newDocumentBuilder();
        } catch (ParserConfigurationException unsupported) {
            throw new IllegalStateException("The XML parser cannot refuse document type declarations", unsupported);
        }
    }
}
