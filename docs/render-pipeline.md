# Render pipeline

A page is built as a tree of renderables and turned into markup in one pass, carrying
with it what it depends on.

## Renderables

A `Renderable` names the Thymeleaf fragment that draws it and carries the data that
fragment reads, the attributes to put on its element, the children nested inside it, its
cacheability, and whatever it needs the page to attach:

```java
Renderable card = Renderable.of("container")
        .attribute("class", "card")
        .cacheTag("node:7")
        .cacheContext("user.permissions")
        .styleSheet("/css/card.css")
        .child(Renderable.of("text").with("value", node.label()));
```

`Renderable.of(type)` draws with a fragment of `render/elements.html`, which holds the
handful core needs: `container`, `text`, `markup`, `link`, `list`, and `listItem`. A
module draws with its own template through `Renderable.of(template, type)`.

A fragment reads the node's data by name, the rendered markup of its children as
`children`, and its attributes as a map:

```html
<div th:fragment="text" th:id="${attributes['id']}" th:classappend="${attributes['class']}"
     th:text="${value}">text</div>
```

Thymeleaf sets attributes it can name, so a fragment reads the attributes it cares
about rather than spreading the whole map onto its element.

## Bubbling

`RenderService.render` returns a `RenderedPage`: the markup, the cacheability merged
from every node in the tree, and everything those nodes asked the page to carry.

Merging is what makes bubbling work. Contexts and tags are unions, so a page carries
every tag any part of it depends on and invalidating one tag drops the page. Max age is
the shorter of the two, so a page is only as reusable as its least reusable part, and
one uncacheable part makes the whole page uncacheable. Attachments merge the same way,
and each style sheet or script is carried once however many nodes asked for it.

## Placeholders

A part that varies per visitor holds back a page that would otherwise be reusable.
`Renderable.lazy` puts a placeholder there instead:

```java
Renderable.of("container")
        .cacheTag("config:system.site")
        .child(Renderable.lazy(() -> Renderable.of("text")
                .with("value", "Signed in as " + account.name())
                .cacheContext("user")
                .maxAge(Duration.ZERO)));
```

The shell renders with a marker in place of the lazy part, and the builder runs once the
shell is done. What it builds is dropped into the marker's place and contributes its own
cacheability from there, so the shell's metadata describes the shell and the page's
describes the whole. A built part may hold placeholders of its own, which are filled in
turn. This is the piece a dynamic page cache and a streamed first render are built on.
