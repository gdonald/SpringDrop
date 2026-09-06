package dev.springdrop.kernel.mail;

/**
 * Mail delivery settings. The address replies go to when it differs from the
 * site mail address; blank leaves the reply-to header off.
 */
public record MailSettings(String replyTo) {

    public static final String CONFIG_NAME = "system.mail";

    public static final MailSettings DEFAULTS = new MailSettings("");
}
