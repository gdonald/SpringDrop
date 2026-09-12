package dev.springdrop.kernel.theme;

/**
 * A message shown to the reader above the page content. The severity chooses the
 * Bootstrap alert the theme draws it as.
 */
public record StatusMessage(Severity severity, String text) {

    public enum Severity {
        SUCCESS("alert-success"),
        WARNING("alert-warning"),
        ERROR("alert-danger"),
        INFO("alert-info");

        private final String alertClass;

        Severity(String alertClass) {
            this.alertClass = alertClass;
        }

        public String alertClass() {
            return alertClass;
        }
    }

    public static StatusMessage success(String text) {
        return new StatusMessage(Severity.SUCCESS, text);
    }

    public static StatusMessage warning(String text) {
        return new StatusMessage(Severity.WARNING, text);
    }

    public static StatusMessage error(String text) {
        return new StatusMessage(Severity.ERROR, text);
    }

    public static StatusMessage info(String text) {
        return new StatusMessage(Severity.INFO, text);
    }

    public String alertClass() {
        return severity.alertClass();
    }
}
