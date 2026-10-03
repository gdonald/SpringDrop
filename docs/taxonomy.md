# Taxonomy

Taxonomy classifies content by terms. Terms come in vocabularies, such as tags or
categories, and each vocabulary's terms form a tree.

## Vocabularies and terms

A vocabulary is the config entity `taxonomy_vocabulary.<id>`, read and written through
`VocabularyManager`, and is the bundle the Field API hangs its terms' fields on.
`Vocabulary` holds its machine name, label, description, and weight. Vocabularies are
listed lightest first and then by label.

A term is the content entity `taxonomy_term`: fieldable, revisionable, and labeled by its
name. Its base fields are:

| Field | Holds |
| --- | --- |
| `status` | whether the term is published |
| `changed` | when the term was last saved |
| `description` | text describing the term |
| `weight` | where the term sorts among its siblings |
| `parent` | the term it sits under, `0` for the top of the tree |

A term stored without them starts published, at the top of the tree, with no description
and a weight of 0, which is how a term created by tagging starts.

`TaxonomyService.install` creates the term tables when the application starts, leaving
tables that exist as they are.

## The tree

`TaxonomyService.save` refuses a parent that would break the tree with
`TermHierarchyException`: a term that does not exist, one in another vocabulary, the term
itself, or a term below it. `tree(vocabulary)` lists the terms in tree order as
`TermTreeItem`s, each term followed by the terms under it, siblings lightest first and then
by name, with how many levels down each sits. A term whose parent is gone is listed at the
top. `descendantsOf(id)` is every term below one term, and ends even for terms stored in a
loop. Deleting a term deletes the terms below it, and deleting a vocabulary's terms is the
step deleting the vocabulary takes.

## Admin and term pages

| Path | Does |
| --- | --- |
| `/admin/structure/taxonomy` | lists the vocabularies, with List terms, Edit, Manage fields, and Delete |
| `/admin/structure/taxonomy/add` | adds a vocabulary, with a machine name from its name |
| `/admin/structure/taxonomy/manage/{vocabulary}` | renames and describes a vocabulary |
| `/admin/structure/taxonomy/manage/{vocabulary}/overview` | arranges the vocabulary's terms |
| `/admin/structure/taxonomy/manage/{vocabulary}/add` | adds a term |
| `/taxonomy/term/{id}` | shows a term with its description and fields |
| `/taxonomy/term/{id}/edit`, `/taxonomy/term/{id}/delete` | change or delete a term |

The admin pages require `administer taxonomy`. The overview lists the terms in tree order,
marking depth with hyphens. Rows drag into order and renumber their weights, and each
row's parent choice offers the top of the tree and every term that is neither the row's
term nor below it. Saving applies every row's parent and weight. A parent that would break
the tree, or a weight that is not a whole number, leaves that part of the term as it was.
The term form offers the same parent choices and refuses a parent that would break the
tree with the reason on the choice.

## Tagging

A node field referring to terms, with `auto_create` turned on and the vocabulary named as
its `auto_create_bundle`, tags content: a name that matches no term creates one for
someone holding `create terms in <vocabulary>`. The `entity_reference_autocomplete_tags`
widget holds every tag in one input, separated by commas.

## Term pages and feeds

`/taxonomy/term/{id}` lists, under the term's description and fields, the published
content tagged with it through any node field referring to terms: teasers, sticky ones
first and then the newest, ten to a page with a pager, leaving out any the person may not
read. The page's head points feed readers at `/taxonomy/term/{id}/feed`, and the page
links to it.

The feed is RSS 2.0: the term's name, address, and description, then an item for each of
the ten newest tagged nodes the person may read, with its title, address, teaser, and
publication date. Addresses start with the site's `url`.

## Access

`TermAccessHandler` opens a published term to anyone holding `access content`. Editing,
deleting, and creating go by the vocabulary: `edit terms in <vocabulary>`,
`delete terms in <vocabulary>`, and `create terms in <vocabulary>`, which
`TaxonomyPermissions` builds from the vocabularies the site has. `administer taxonomy`
allows everything.
