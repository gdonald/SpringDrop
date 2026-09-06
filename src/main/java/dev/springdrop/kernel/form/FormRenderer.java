package dev.springdrop.kernel.form;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders a form tree to Bootstrap markup: each control wrapped with its label
 * and help text, and each error shown as invalid feedback on the field it
 * belongs to. Elements the current user may not see are left out. Buttons are
 * solid, never outline-style, matching the rest of the admin.
 */
@Component
public class FormRenderer {

    private static final String CONTROL_CLASS = "form-control";

    private static final String CHECK_INPUT_CLASS = "form-check-input";

    public String render(FormElement element) {
        return render(element, Map.of());
    }

    /** Renders the form with the errors of a submission bound to their fields. */
    public String render(FormElement element, Map<String, String> errors) {
        if (!element.access()) {
            return "";
        }
        return switch (element.type()) {
            case CONTAINER -> wrapper("div", "", element, errors);
            case FIELDSET -> fieldset(element, errors);
            case DETAILS -> details(element, errors);
            case VERTICAL_TABS -> wrapper("div", "nav flex-column nav-pills", element, errors);
            case ACTIONS -> wrapper("div", "d-flex gap-2", element, errors);
            case VALUE -> "";
            case TEXT -> text(element);
            case LINK -> link(element);
            case HIDDEN -> hidden(element);
            case SUBMIT -> submit(element);
            case CHECKBOX -> field(element, errors, checkbox(element, errors));
            case RADIOS -> field(element, errors, choices(element, errors, "radio"));
            case CHECKBOXES -> field(element, errors, choices(element, errors, "checkbox"));
            case SELECT -> field(element, errors, select(element, errors));
            case TEXTAREA -> field(element, errors, textarea(element, errors));
            case TEXTFIELD -> field(element, errors, input("text", element, errors));
            case PASSWORD -> field(element, errors, password(element, errors));
            case NUMBER -> field(element, errors, input("number", element, errors));
            case DATE -> field(element, errors, input("date", element, errors));
            case TIME -> field(element, errors, input("time", element, errors));
            case DATETIME -> field(element, errors, input("datetime-local", element, errors));
            case FILE -> field(element, errors, input("file", element, errors));
        };
    }

    private String renderChildren(FormElement element, Map<String, String> errors) {
        StringBuilder markup = new StringBuilder();
        for (FormElement child : element.children()) {
            markup.append(render(child, errors));
        }
        return markup.toString();
    }

    private String wrapper(String tag, String cssClass, FormElement element, Map<String, String> errors) {
        return "<" + tag + classAttribute(cssClassOf(element, cssClass)) + attributes(element) + ">"
                + renderChildren(element, errors)
                + "</" + tag + ">";
    }

    private String fieldset(FormElement element, Map<String, String> errors) {
        String error = errors.get(element.name());
        return "<fieldset" + classAttribute(cssClassOf(element, "mb-3")) + attributes(element) + ">"
                + "<legend class=\"h6\">" + escape(element.label()) + "</legend>"
                + renderChildren(element, errors)
                + (error == null ? ""
                        : "<div class=\"invalid-feedback d-block\">" + escape(error) + "</div>")
                + "</fieldset>";
    }

    private String details(FormElement element, Map<String, String> errors) {
        return "<details" + classAttribute(cssClassOf(element, "mb-3")) + attributes(element) + ">"
                + "<summary>" + escape(element.label()) + "</summary>"
                + renderChildren(element, errors)
                + "</details>";
    }

    /** A line of text in the form, such as the question a confirm form asks. */
    private static String text(FormElement element) {
        return "<p" + classAttribute(cssClassOf(element, "")) + attributes(element) + ">"
                + escape(element.label()) + "</p>";
    }

    /** A link rendered as a button, the way the rest of the admin renders actions. */
    private static String link(FormElement element) {
        return "<a" + classAttribute(cssClassOf(element, "btn btn-secondary"))
                + " href=\"" + escape(valueOf(element)) + "\"" + attributes(element) + ">"
                + escape(element.label()) + "</a>";
    }

    private static String hidden(FormElement element) {
        return "<input type=\"hidden\" name=\"" + escape(element.name()) + "\""
                + " value=\"" + escape(valueOf(element)) + "\">";
    }

    private static String submit(FormElement element) {
        return "<button type=\"submit\"" + classAttribute(cssClassOf(element, "btn btn-primary"))
                + " name=\"" + escape(element.name()) + "\"" + attributes(element) + ">"
                + escape(element.label()) + "</button>";
    }

    /** The label, control, help text, and error message of one field. */
    private String field(FormElement element, Map<String, String> errors, String control) {
        String error = errors.get(element.name());
        StringBuilder markup = new StringBuilder("<div class=\"mb-3\">");
        if (!element.label().isEmpty() && element.type() != ElementType.CHECKBOX) {
            markup.append("<label class=\"form-label\" for=\"").append(escape(element.name())).append("\">")
                    .append(escape(element.label()))
                    .append(element.required() ? "<span class=\"text-danger\"> *</span>" : "")
                    .append("</label>");
        }
        markup.append(control);
        if (!element.description().isEmpty()) {
            markup.append("<div class=\"form-text\" id=\"").append(escape(element.name()))
                    .append("-help\">").append(escape(element.description())).append("</div>");
        }
        if (error != null) {
            markup.append("<div class=\"invalid-feedback d-block\">").append(escape(error)).append("</div>");
        }
        return markup.append("</div>").toString();
    }

    private static String input(String inputType, FormElement element, Map<String, String> errors) {
        return "<input type=\"" + inputType + "\""
                + classAttribute(controlClass(element, errors))
                + identity(element)
                + " value=\"" + escape(valueOf(element)) + "\""
                + requiredAttribute(element)
                + attributes(element)
                + ">";
    }

    /** A password control never carries its value back to the browser. */
    private static String password(FormElement element, Map<String, String> errors) {
        return "<input type=\"password\""
                + classAttribute(controlClass(element, errors))
                + identity(element)
                + requiredAttribute(element)
                + attributes(element)
                + ">";
    }

    private static String textarea(FormElement element, Map<String, String> errors) {
        return "<textarea" + classAttribute(controlClass(element, errors)) + identity(element)
                + requiredAttribute(element) + attributes(element) + ">"
                + escape(valueOf(element)) + "</textarea>";
    }

    private static String select(FormElement element, Map<String, String> errors) {
        StringBuilder markup = new StringBuilder("<select")
                .append(classAttribute(errors.containsKey(element.name())
                        ? cssClassOf(element, "form-select") + " is-invalid"
                        : cssClassOf(element, "form-select")))
                .append(identity(element))
                .append(requiredAttribute(element))
                .append(attributes(element))
                .append(">");
        for (SelectOption option : element.options()) {
            markup.append("<option value=\"").append(escape(option.value())).append("\"")
                    .append(option.value().equals(valueOf(element)) ? " selected" : "")
                    .append(">").append(escape(option.label())).append("</option>");
        }
        return markup.append("</select>").toString();
    }

    private static String checkbox(FormElement element, Map<String, String> errors) {
        return "<div class=\"form-check\">"
                + "<input type=\"checkbox\"" + classAttribute(checkClass(element, errors))
                + identity(element)
                + (Boolean.TRUE.equals(element.value()) ? " checked" : "")
                + requiredAttribute(element) + attributes(element) + ">"
                + "<label class=\"form-check-label\" for=\"" + escape(element.name()) + "\">"
                + escape(element.label()) + "</label>"
                + "</div>";
    }

    private static String choices(FormElement element, Map<String, String> errors, String inputType) {
        StringBuilder markup = new StringBuilder();
        List<SelectOption> options = element.options();
        for (int index = 0; index < options.size(); index++) {
            SelectOption option = options.get(index);
            String id = element.name() + "-" + index;
            markup.append("<div class=\"form-check\">")
                    .append("<input type=\"").append(inputType).append("\"")
                    .append(classAttribute(checkClass(element, errors)))
                    .append(" id=\"").append(escape(id)).append("\"")
                    .append(" name=\"").append(escape(element.name())).append("\"")
                    .append(" value=\"").append(escape(option.value())).append("\"")
                    .append(option.value().equals(valueOf(element)) ? " checked" : "")
                    .append(attributes(element))
                    .append(">")
                    .append("<label class=\"form-check-label\" for=\"").append(escape(id)).append("\">")
                    .append(escape(option.label()))
                    .append("</label>")
                    .append("</div>");
        }
        return markup.toString();
    }

    private static String controlClass(FormElement element, Map<String, String> errors) {
        String base = cssClassOf(element, CONTROL_CLASS);
        return errors.containsKey(element.name()) ? base + " is-invalid" : base;
    }

    /** The class the element asks for, or the one its kind gets by default. */
    private static String cssClassOf(FormElement element, String defaultClass) {
        return element.attributes().getOrDefault("class", defaultClass);
    }

    private static String checkClass(FormElement element, Map<String, String> errors) {
        return errors.containsKey(element.name()) ? CHECK_INPUT_CLASS + " is-invalid" : CHECK_INPUT_CLASS;
    }

    private static String identity(FormElement element) {
        return " id=\"" + escape(element.name()) + "\" name=\"" + escape(element.name()) + "\"";
    }

    private static String requiredAttribute(FormElement element) {
        return element.required() ? " required" : "";
    }

    private static String classAttribute(String cssClass) {
        return cssClass.isEmpty() ? "" : " class=\"" + escape(cssClass) + "\"";
    }

    /** The element's own attributes, plus its state conditions as data attributes. */
    private static String attributes(FormElement element) {
        StringBuilder markup = new StringBuilder();
        element.attributes().forEach((attributeName, attributeValue) -> {
            if (!"class".equals(attributeName)) {
                markup.append(" ").append(attributeName).append("=\"").append(escape(attributeValue)).append("\"");
            }
        });
        element.states().forEach(state ->
                markup.append(" data-state-").append(state.condition())
                        .append("=\"").append(escape(state.selector())).append("\""));
        if (element.required()) {
            markup.append(rule(ValidationRule.REQUIRED, "", ValidationRule.REQUIRED_MESSAGE));
        }
        element.rules().forEach(validationRule ->
                markup.append(rule(validationRule.type(), validationRule.parameter(), validationRule.message())));
        return markup.toString();
    }

    /** One rule as the script reads it: the rule itself and the message to show. */
    private static String rule(String type, String parameter, String message) {
        return " data-rule-" + type + "=\"" + escape(parameter) + "\""
                + " data-rule-" + type + "-message=\"" + escape(message) + "\"";
    }

    private static String valueOf(FormElement element) {
        return (element.value() == null) ? "" : element.value().toString();
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text);
    }
}
