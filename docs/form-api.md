# Form API

A form is a tree of elements plus the code that checks and acts on what comes back.
Core renders the tree to Bootstrap markup, so a module describes a form rather than
writing HTML.

## Elements

`FormElement.of(type, name)` builds one node, and fluent calls fill it in:

```java
FormElement.of(ElementType.CONTAINER, "subscribe")
        .child(FormElement.of(ElementType.TEXTFIELD, "mail")
                .label("Email")
                .description("We send one message a month")
                .markRequired())
        .child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Subscribe")));
```

The element types are containers (`CONTAINER`, `FIELDSET`, `DETAILS`, `VERTICAL_TABS`,
`ACTIONS`), controls (`TEXTFIELD`, `TEXTAREA`, `SELECT`, `RADIOS`, `CHECKBOXES`,
`CHECKBOX`, `NUMBER`, `DATE`, `FILE`), and the ones that carry no control of their own
(`HIDDEN`, `VALUE`, `SUBMIT`).

`withoutAccess()` marks an element the current user may not see. It is left out of the
rendered form and is not enforced when required.

## Conditions

An element can depend on what another element holds:

```java
FormElement.of(ElementType.TEXTFIELD, "address")
        .label("Address")
        .visibleWhen("method", "post")
        .requiredWhen("method", "post");
```

Each condition renders as `data-state-visible="method:post"` and the like.
`form-states.js` reads those attributes and shows, requires, or disables the control as
the other control changes. The server reads the same conditions from the same tree: a
field hidden by its conditions is not enforced when required, and one whose conditions
make it required is enforced even though the element itself is not marked required. A
browser with no scripting reaches the same answer, since the server decides.

## Rendering

`FormRenderer` maps each element to its Bootstrap control: `form-control` inputs and
textareas, `form-select` selects, `form-check` radios, checkboxes and single
checkboxes, and solid `btn-primary` submit buttons, never outline-style. Every field is
wrapped with its label, its help text as `form-text`, and, when the submission carries
an error for it, `is-invalid` on the control plus the message as `invalid-feedback`.
Labels and values are escaped, so a value carrying markup cannot alter the form.

## Lifecycle

A form is a bean implementing `Form`: `build` returns the tree, `validate` records
errors, and `submit` acts on values that passed. `FormBuilder` runs it:

1. `build` produces the tree, and a `FormAlterEvent` lets modules add, remove, or
   change elements before anything else happens.
2. Required elements the user can see are checked first, then the form's own
   `validate` runs, so a form's rules see values that are actually there.
3. A form with errors is rendered again with them, and nothing is submitted. A form
   whose validation asked to rebuild is rendered again for the next step, keeping what
   `FormState.store` held. Anything else is submitted.

`FormState` carries the submitted values, the errors, the storage a multi-step form
keeps its work in, the triggering element, and the rebuild flag. `FormOutcome` reports
whether the form submitted, along with the markup to send back when it did not.

## Rules

A rule is written once and enforced twice:

```java
FormElement.of(ElementType.TEXTFIELD, "mail")
        .label("Email")
        .markRequired()
        .rule(ValidationRule.maxLength(255))
        .rule(ValidationRule.email());
```

`FormBuilder` checks each rule on submission. The renderer hands the browser the same
rule and the same message as `data-rule-email` and `data-rule-email-message`, and
`form-validation.js` checks them before the form is sent. The script carries no wording
of its own, so the two sides cannot drift: a rule reads the same either way, which the
backend test and the Vitest suite assert against the same messages. `required`,
`maxlength`, `pattern`, `email`, and `range` are the rules core ships; a range with an
empty end is open on that side.

## Confirming a destructive action

`ConfirmForm` is the base for anything that cannot be undone. A form names the question,
what will happen, where cancel goes back to, and what confirming carries out:

```java
class DeleteArticleForm extends ConfirmForm {
    protected String question() { return "Delete the article Hello?"; }
    protected String cancelPath() { return "/articles"; }
    protected String confirmLabel() { return "Delete"; }
    protected void confirmed(FormState state) { articles.delete(id); }
}
```

It renders the question, the description, one confirm button, and a cancel link styled
as a button. Nothing happens until the confirming POST arrives, so no link can carry
out the action on its own.

## Serving a form

`FormController` serves every registered form at `/form/{formId}`. A GET renders the
themed page; a POST validates and submits. The same POST answers both kinds of client:
htmx gets the form markup on its own to swap in place, or an `HX-Redirect` header once
it submitted, while a browser without scripting gets the whole page again with its
errors, or a redirect. Nothing about the outcome depends on which one asked.

## Field widgets

A widget turns one field's values into form elements and reads them back. It is a
plugin registered with `@SpringDropPlugin(type = FieldWidget.class)`, and it is asked
for one element per delta; cardinality is handled around it by `FieldWidgetManager`.

```java
FormElement field = widgets.build(widgets.context("node", "article", "tags"), values, 0);
```

The widget comes from the instance's `widget` setting, or from the field type's
default. A single-value field renders one control named after the field; a multi-value
field renders one per value, named `tags[0]`, `tags[1]`, and so on, and only the first
delta carries the label and the required mark, since a required field needs one value
rather than every value it could hold.

Core ships `string_textfield`, `string_textarea`, `number`, `boolean_checkbox`,
`options_select`, `options_buttons`, and `datetime_default`. The number widget writes
the field's bounds as native `min`, `max`, and `step` attributes and as the rule the
server checks. The boolean widget reads a missing value as off, since a browser does
not send an unticked box. The options widgets offer an empty choice unless the field is
required, and `options_buttons` renders radios for a single value and checkboxes for
several.

A date field gets a native date input when its storage holds dates, and a
`datetime-local` input when it holds moments. What the person types is read in the
site's timezone and stored as an instant, and a stored instant is shown back as the
wall clock reading of that timezone, so a moment reads the way it was typed. A
`daterange` field gets one input per end, grouped in a fieldset so an error about the
range lands on the pair rather than on one end of it.

While a field can hold more values, the widget adds an **Add another item** button that
posts the form to `/field/add-more` and swaps the field back in one delta longer, so
nothing else on the page is touched and nothing entered is lost.

### Referring to another entity

A reference field gets an autocomplete input. It suggests targets from
`/field/autocomplete` as the person types, filtered to what they are allowed to see,
and holds the one they pick as `Label (id)`. A field whose instance turns on
`auto_create` accepts a name that matches nothing and creates the target as the
reference is read, which is how tagging works. Creating an entity with no id of its own
allocates the next one from its storage.

## Form displays

`FormDisplayConfig` is a config entity naming, for one bundle and one form mode, which
fields the edit form shows, in what order, through which widget, and which are moved to
the disabled region. `FormDisplayManager` builds the fields from it:

```java
displays.save(FormDisplayConfig.of("node", "article", FormDisplayConfig.DEFAULT_MODE)
        .with(FieldDisplaySlot.of("body", TextareaWidget.ID, 0))
        .with(FieldDisplaySlot.of("tags", TextfieldWidget.ID, 10))
        .withoutField("legacy_notes"));
```

A bundle with no display saved shows every field it has, in the order the fields were
added. Each form mode is laid out on its own.

## Field formatters

A formatter renders one value of a field for reading. It is a plugin registered with
`@SpringDropPlugin(type = FieldFormatter.class)`, and `FieldFormatterManager` renders
the field around it: the label, then the value, or a list of values in delta order when
the field holds several. A field holding nothing renders nothing at all.

Core ships `string` and `basic_string` for text, `number_integer` and `number_decimal`
with prefix, suffix, separator, and decimal-place settings, `boolean` with configurable
labels for the two states, and `list_default`, which shows an option's label rather than
its stored value. Stored values are escaped, so nothing saved can alter the page.

`datetime_default`, `timestamp`, and `daterange_default` render moments in one of the
site's named date formats, in the site's timezone or one the display names. With
`time_ago` turned on a moment reads as how long ago it was instead, counted in the
largest unit that still says something. `link` renders an anchor shown by its title or
by where it goes, pointing an internal link at the path its route serves and marking an
external one so the page it opens cannot reach back.

`entity_reference_label` shows the target's label, linked to it through the type's
canonical route unless the display says otherwise, `entity_reference_entity_id` shows
the bare id, and `entity_reference_entity_view` renders the target in a named view mode.
An entity already being rendered further up the page is not rendered again, so a
reference pointing back at its own page ends rather than looping. A reference to
something no longer there shows its id, so the dangling reference is visible.

## View displays

`ViewDisplayConfig` is the reading counterpart of the form display: per bundle and view
mode, which fields are shown, in what order, through which formatter, whose labels are
shown, and which fields are left out. `ViewDisplayManager` renders from it, and each
mode is laid out on its own, so changing the teaser leaves the full display alone.

```java
displays.save(ViewDisplayConfig.of("node", "article", ViewDisplayConfig.TEASER_MODE)
        .with(FieldDisplaySlot.of("summary", BasicStringFormatter.ID, 0))
        .withoutLabel("summary")
        .withoutField("body"));
```

## Field UI

The field admin lives under `/admin/structure/{entity type}/{bundle}`, behind the
`administer fields` permission:

- `/fields` lists what the bundle has, each row with an Edit and a Delete button, and
  offers the form that adds another. Adding runs in two steps, since the settings the
  second step asks for depend on the type chosen in the first. Saving creates the field
  storage, the instance, and the field's tables. Deleting asks first, then removes the
  storage, every instance of it, and the tables.
- `/form-display` places each field on the edit form: its widget, its weight, and
  whether it is shown at all. A field taken off the form leaves the edit form; changing
  the weights reorders it.
- `/display` does the same for reading, and adds whether each label is shown. Each view
  mode is laid out on its own, so changing the teaser leaves the default alone.

Both display tabs read the handlers back from the plugin registry, so a widget or
formatter a module adds is offered without core listing it.
