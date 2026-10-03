# Search

Search finds entities by keyword. Entities are turned into documents, each a title and
the text of a body, and a search backend keeps the documents and matches keywords against
them.

## Documents

A `SearchDocument` holds the entity type, the entity id, the langcode, the title, and the
body. A module makes an entity type searchable by registering a `SearchDocumentBuilder`
bean for it, which turns an entity into its document.

`TextFieldsDocumentBuilder` makes a document of an entity's label and the text of its
`string`, `string_long`, `text`, `text_long`, and `text_with_summary` fields, every value
of each, with HTML tags removed. A summary is indexed along with its text.
`NodeSearchDocuments` uses it for content and `UserSearchDocuments` for accounts, whose
mail address is a base field and is not indexed.

## Indexing

`SearchIndexer` keeps the index up to date with the searchable entity types:

- Saving an entity of a searchable type marks it pending in the `search_pending` table.
- The cron job `search.index` indexes up to 100 pending entities each run. An entity gone
  since it was marked is removed from the index instead.
- Deleting an entity removes it from the index and from the pending list at once.
- Indexing an entity invalidates its type's list tag, such as `node_list`, so a search
  kept in a cache shows the index as it is now.

`indexPending(limit)` indexes up to `limit` pending entities and gives how many it took,
`pending()` gives how many wait, and `markAllPending()` marks every entity of the
searchable types pending, so the following runs index all of them again.

## Searching

`SearchService.search(new SearchQuery(entityType, keywords, offset, limit))` gives
`SearchResults`: one page of `SearchHit`s, best first, and how many documents matched in
all. A hit holds the entity type, id, langcode, title, rank, and an excerpt.

`SearchService.ranked(entityType, keywords)` gives the best 500 hits for the keywords.
During a request it keeps them on the request, so the handlers drawing one view ask the
backend once.

The keywords are read as words: runs of letters and digits, lower case, each once, and at
most 32 of them. A document matches when it holds every word. Keywords with no words
match nothing.

## The Postgres backend

`PostgresSearchBackend`, with the id `postgres`, keeps documents in the `search_index`
table, one row per entity and langcode. Its `document` column is a `tsvector` Postgres
generates from the title and body with the `english` text search configuration, the
title weighted above the body, and a GIN index covers it. Words are reduced to their
stems, so `bridge` finds `Bridges`.

A search matches through `to_tsquery`, ranks with `ts_rank`, highest first and then the
newest id, and excerpts the body with `ts_headline`. The excerpt is HTML: its text
escaped, and each matched word in a `mark` element.

## The Lucene backend

`LuceneSearchBackend`, with the id `lucene`, keeps documents in a Lucene index on disk,
in the directory `springdrop.search.lucene-path` names, `search-index` by default, read
from the directory the site runs in. Titles and bodies are analyzed with Lucene's English
analyzer, which also reduces words to their stems. A search matches every keyword in the
title or the body, a title match counting twice, ranks by score and then the newest id,
and excerpts the body with Lucene's highlighter, escaped and marked as the Postgres
backend does. A document with no keyword in its body is excerpted with the body's first
35 words. The index is opened for each operation, so more than one instance of the site
in one process can share the directory.

## Choosing a backend

The config object `search.settings` names the backend the site searches through,
`postgres` unless it names another. `SearchService.choose(id)` names another, refusing a
backend the site does not have, and a stored name the site does not have reads as
`postgres`.

`SearchIndexer.reindex()` builds the index of the backend in use again: it removes every
document of the searchable types from it, marks every entity of them pending, and
indexes them all. `SearchIndexer.switchBackend(id)` chooses the backend and then
reindexes. Each backend keeps its own index, so one that is not in use is left as it
was.

`/admin/config/search/settings` chooses the backend and needs `administer search`.
Saving it switches to the chosen backend and rebuilds that backend's index, the same
backend included.

Starting the site with `--search-reindex`, such as `java -jar springdrop.jar
--search-reindex`, reindexes the backend in use once the site is ready, and logs how
many entities it indexed.

## The backend interface

`SearchBackend` is the interface a backend implements: `index` adds or replaces a
document, `remove` takes away an entity's documents in every language, `clear` takes away
every document of an entity type, and `search` answers a query.

## Searching in views

The `search_keywords` filter keeps the results whose documents hold the keywords, and
orders them by rank ahead of the view's sorts. Its `entity_type` setting names the
documents searched, and the property it reads holds their entity ids, `id` for the base
entity. Exposed, it is drawn as a search box. Keywords with no words find nothing, and
access is checked after the best 500 hits, as for any view.

The `search_excerpt` field draws the excerpt of a result's document for the keywords the
reader gave under its `identifier` setting, `keys` by default. A result the keywords did
not find draws nothing.

## Search pages

A `SearchPage` is a config object stored as `search.page.<id>`: a label, the path under
`/search` it answers at, the view drawing its form and results, and a weight ordering the
pages. `SearchPageManager` adds two when the site starts, leaving any already saved as
they are, and adds the views they draw when those are missing:

| Page | Path | View | Lists | Access |
| --- | --- | --- | --- | --- |
| `node_search`, Content | `/search/node` | `search_content` | content holding the keywords, each title linked over its excerpt, 10 to a page | `search content` |
| `user_search`, Users | `/search/user` | `search_users` | active accounts whose names hold the keywords, 10 to a page | `search users` |

`search_content` checks the view access of each result, so a reader finds only content
they may view. `search_users` lists accounts without checking each one.

`/search/<path>` draws the page's view with the reader's `keys`: the search form alone
until keywords are given, then the form over the results, with "Your search yielded no
results." when nothing matches. A tab leads to each search page the reader may use.
`/search` sends the reader to the first of them. A page whose view is missing, or whose
view's access plugin turns the reader away, is answered with 403, and a path no page
answers at with 404.

`search content`, `search users`, and `administer search` are declared in
`search.permissions.yml`.
