# Languages

A site is shown in one or more languages. Each request is in one of them, chosen by
negotiation, and the page's `<html>` carries its code as `lang` and its direction as
`dir`.

## Languages

A language is the config object `language.entity.<langcode>` (`LanguageConfig`):

| Key | Holds |
| --- | --- |
| `id` | the language code, such as `fr` or `pt-br` |
| `label` | its name |
| `direction` | `ltr` or `rtl` |
| `weight` | its place among the languages, lowest first |
| `prefix` | the path prefix that selects it, such as `fr` for `/fr/about`, or empty for none |
| `domain` | the host name that selects it, such as `fr.example.com`, or empty for none |

`LanguageManager` adds English when the site starts with no languages. `all()` gives the
languages by weight and name. The default language is the config object
`language.settings` (`defaultLangcode`), English unless set, and `delete` refuses it.
`current()` gives the language of the request being answered.

`/admin/config/regional/language` lists the languages with Edit and Delete buttons, the
default marked and without Delete. The add and edit forms take the fields above and a
**Make this the default language** box. A code another language has, or a prefix or
domain another language uses, is refused. The pages need `administer languages`.

## Negotiation

The config object `language.negotiation` (`NegotiationSettings`) lists the methods tried,
in order, and whether the URL method reads a prefix or the domain. The first method that
names a language the site has wins. When none does, the request is in the default
language.

| Method | Names |
| --- | --- |
| `url` | with `prefix`, the language whose prefix starts the path, and with `domain`, the language whose domain was asked for |
| `user` | the signed-in account's preferred language |
| `session` | the language a `?language=<code>` parameter chose, kept in the session for the requests after it |

The default is `url` with prefixes, then `user`. `/admin/config/regional/language/detection`
turns methods on and orders them, and chooses the URL source.

Negotiation runs in two filters. `LanguageUrlFilter` runs before path aliases and the
security chain. It answers `/fr/about` as `/about`, so aliases resolve and access is
checked on the path without its prefix, and `/fr` alone as `/`. `LanguageNegotiationFilter`
runs once the account is known and before the page cache, and makes the language the
request's locale. `NegotiatedLocaleResolver` is Spring MVC's `localeResolver`, so
`LocaleContextHolder` and Thymeleaf's `#locale` hold it. Pages vary by the `languages`
cache context.

An account's preferred language is set on its edit form at `/admin/people/{id}/edit`.

Links the site draws do not carry the language prefix yet. A page reached by a prefix
links to the other pages without one.

## Content translation

An entity of a translatable type, such as a node, is stored in its own language, the
`langcode` of its base row, and may be translated into others. A translation holds its own
label and its own values of the translated fields. Every other field is shared: base
fields such as `status` and `owner`, and the configurable fields not marked translated.
Saving any translation changes the shared fields for all of them.

`TranslatableFields` keeps which configurable fields of an entity type are translated, as
the config object `content_translation.<entity type>`. The Field UI's edit form for a field
of a translatable type has a **Users may translate this field** box.

`EntityCrudService` handles translations in its usual calls:

- `save(entity)` of a stored entity in another language saves that translation: its label
  goes in `<type>_translation`, its translated fields are written in its language, and its
  shared fields and base fields are written in the entity's own language and base row. The
  entity's own label stays as it was.
- `load(type, id, langcode, revision)` reads the translation into the language, or the
  entity in its own language when it has none. The result's `langcode` says which.
- `translations(type, id)` gives the entity's own language, then the languages it is
  translated into.
- `deleteTranslation(type, id, langcode)` removes a translation's label and translated
  values. The entity's own language cannot be removed this way.

Revisions record the entity's own label and base fields. A translation's label is not
kept per revision.

An entity query `inLanguage(langcode)` finds the entities in that language, their own or a
translation, and reads translated fields in that language and shared ones in the entity's
own. A query in no language reads every field in the entity's own language.

A node is shown and edited in the request's language: `/fr/node/4` shows the French
translation, or the node in its own language when it has none, and `/fr/node/4/edit` edits
it. The edit form also takes `?langcode=fr` to edit a translation whatever the request's
language.

`/node/{id}/translations`, the node's Translate tab, lists every language with the node's
title in it and whether it is the original, translated, or not translated. **Add** copies
the node into the language and opens the copy's edit form, **Edit** opens a translation's
form, and **Delete** removes a translation after confirmation. The tab shows on a site with
more than one language, for someone who may change the node and holds `translate content`.
Adding and deleting a translation are logged on the `content` channel.

## Language switcher

The `language_block` block links to the page being read in each language, the current one
active. A link uses the language's prefix or domain when the URL method is on, and the
`language` parameter when only the session method is. A site with one language, or with
neither method on, shows nothing. `LanguageLinks.url(language, path)` builds the links.

## Interface translation

The words the site's pages are built from are written in English and translated into the
other languages. A template asks for one through the `translator` bean:

```html
<span th:text="${@translator.t('Unpublished')}">Unpublished</span>
```

`Translator.t(source)` and `t(source, context)` give the translation into the request's
language. A context tells apart two uses of the same words, such as `May` the month and
`May` the verb. An English request gets the source as it is. A string with no translation
is shown in English and recorded in `locale_source`, so the translation page lists it.
`plural(count, singular, plural)` gives the first form for one and the second for any
other count, with `@count` replaced. A plural string keeps its forms joined by U+0003, as
gettext tools and Drupal do.

Translations live in `locale_target`, one per string and language. Each language's are
read once and kept in memory. Saving a translation drops them and invalidates the
`locale` cache tag, which every page and every view listing carries, and view listings
are kept per language.

The strings translated so far are the Unpublished badge on content and media, the pager's
Previous and Next, and the comment's Submitted by.

| Path | Does |
| --- | --- |
| `/admin/config/regional/translate` | lists the strings with a box for each one's translation into the chosen language, filtered by words in the source or translation and by untranslated only, 50 a page. A plural string has a box per form. Emptying a box removes the translation. |
| `/admin/config/regional/translate/import` | reads a `.po` file into a language's translations |
| `/admin/config/regional/translate/export` | downloads a language's translations as `springdrop.<langcode>.po` |

The pages need `translate interface` and offer every language but English.

`PoFile` reads and writes gettext `.po` files: `msgctxt`, `msgid`, `msgid_plural`,
`msgstr`, and `msgstr[n]`, with strings continued on the lines after them and `\n`,
`\t`, `\"`, and `\\` escapes. Comments, the header, and entries with no translation are
skipped. A file it cannot read is refused with the line where it went wrong, and nothing
is imported.

## Configuration translation

The names, help, and titles config objects show are translated apart from the objects. A
translation of one object into one language is the config object
`config_translation.<langcode>.<config name>` (`ConfigTranslation`), holding the translated
string for each translated path. The object itself is left as it is.

`LanguageConfigOverrides` is a config override provider. A request in a language other
than English reads a translated object with its translated strings in place, so a field's
label, a view's title, or the site name shows in that language wherever it is read. An
administration route reads every object as stored, so editing a field on `/fr/admin/...`
shows and saves its source label.

`ConfigTranslationMapper` says which config can be translated: a name prefix, what the
kind is called, and the translatable paths. A path is a top-level key, such as `label`, or
`displays.*.title` for the title of each item in a list, told apart by the item's `id`. A
module adds kinds by registering a `ConfigTranslationMappers` bean. Core's are:

| Kind | Config | Paths |
| --- | --- | --- |
| Field | `field.instance.*` | `label`, `description` |
| Content type | `node_type.*` | `label`, `description`, `help`, `titleLabel` |
| Vocabulary | `taxonomy_vocabulary.*` | `label`, `description` |
| Menu | `system.menu.*` | `label`, `description` |
| View | `views.view.*` | `label`, `description`, `displays.*.title` |
| Contact form | `contact_form.*` | `label`, `reply` |
| Site information | `system.site` | `name`, `slogan` |

Menu links declared in code are interface strings: a built menu shows their titles through
`Translator.t`, and menus vary by the `languages` cache context. Menu links stored on the
site keep the title they were saved with.

`/admin/config/regional/config-translation` lists every stored object that can be
translated, each object's page lists the languages other than English, and a language's
form shows each source string with a box for its translation. Emptying every box removes
the translation. Saving one invalidates the object's cache tag and the `locale` tag. The
pages need `translate configuration`.
