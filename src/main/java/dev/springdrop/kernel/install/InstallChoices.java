package dev.springdrop.kernel.install;

import java.io.Serializable;

/**
 * What the person installing chose so far, kept in their session from step to
 * step: the site's language, the profile, and the site and administrator
 * details. A step not yet taken is null.
 */
public record InstallChoices(String langcode, String profile) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final InstallChoices NONE = new InstallChoices(null, null);

    public InstallChoices withLangcode(String newLangcode) {
        return new InstallChoices(newLangcode, profile);
    }

    public InstallChoices withProfile(String newProfile) {
        return new InstallChoices(langcode, newProfile);
    }
}
