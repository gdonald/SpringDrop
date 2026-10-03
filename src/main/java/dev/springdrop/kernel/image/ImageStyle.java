package dev.springdrop.kernel.image;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A named way of drawing images, such as a thumbnail: the effects applied to the
 * original, lightest first. Stored as the config object {@code image.style.<id>}.
 */
public record ImageStyle(String id, String label, List<EffectConfig> effects) {

    public static final String CONFIG_PREFIX = "image.style";

    public ImageStyle {
        effects = effects.stream().sorted(Comparator.comparingInt(EffectConfig::weight)).toList();
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    public Optional<EffectConfig> effect(String effectId) {
        return effects.stream().filter(effect -> effect.id().equals(effectId)).findFirst();
    }

    public ImageStyle withLabel(String newLabel) {
        return new ImageStyle(id, newLabel, effects);
    }

    /** The style with the effect added, or put in place of the one with the same id. */
    public ImageStyle withEffect(EffectConfig effect) {
        List<EffectConfig> changed = new ArrayList<>(effects.stream()
                .filter(existing -> !existing.id().equals(effect.id())).toList());
        changed.add(effect);
        return new ImageStyle(id, label, changed);
    }

    public ImageStyle withoutEffect(String effectId) {
        return new ImageStyle(id, label, effects.stream().filter(effect -> !effect.id().equals(effectId)).toList());
    }
}
