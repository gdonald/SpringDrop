package dev.springdrop.kernel.field.widget;

/**
 * Where the widget actions post to. The add-another button names this path, and
 * the controller serving it answers with the field rendered one delta longer.
 */
public interface FieldWidgetPaths {

    String ADD_MORE = "/field/add-more";

    String AUTOCOMPLETE = "/field/autocomplete";

    /** Where an editor sends an image to insert into formatted text. */
    String EDITOR_UPLOAD = "/editor/upload";
}
