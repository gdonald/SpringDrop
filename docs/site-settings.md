# Site settings

The settings forms under `/admin/config/system` need `administer site configuration`.

## Basic site settings

`/admin/config/system/site-information` sets:

| Field | Stored in | Checked |
| --- | --- | --- |
| Site name | `system.site` `name` | required, at most 128 characters |
| Slogan | `system.site` `slogan` | at most 255 characters |
| Email address, the address the site's mail is sent from | `system.site` `mail` | required, an email address |
| Default front page | `system.site` `frontPage` | a path starting with `/`; blank shows the front page listing, `/node` |
| Default 403 page and default 404 page | `system.error_pages` | a path starting with `/`, or blank for the default page |
| Default timezone | `system.date` `defaultTimezone` | required, one of the Java runtime's zone ids |
| Default language | `language.settings` `defaultLangcode` | required, one of the site's languages |

The home page shows the default front page's page, and the site name and slogan appear in
the theme. A form with a field that fails its check saves nothing.

## Regional settings

`/admin/config/regional/settings` sets the site's default country, an ISO 3166 code or none,
the day its week starts on, and its default timezone, the same timezone the basic site
settings set. They are stored in `system.date`. Date and date range widgets carry the first
day of the week as `data-first-day`, from 0 for Sunday to 6 for Saturday, for a calendar to
lay out its weeks. A country, day, or timezone that is not one of those offered is refused.

## RSS publishing

`/admin/config/services/rss-publishing` sets, in `system.rss`, how many items each feed
holds, from 1 to 100 and 10 by default, and the description each feed's channel carries,
the site name when blank. A feed display lists at most that many of its view's results.

## Content type publishing defaults

Each content type's edit form sets whether its new nodes start published, promoted to the
front page, and sticky, and whether each save makes a new revision. A new node takes those
values from its content type.

## Performance

`/admin/config/development/performance` sets, in `system.performance`:

| Field | Does |
| --- | --- |
| Page cache maximum age | how long a page is kept in the page cache, from no caching to a day |
| Aggregate JavaScript files | whether pages load the site's scripts as the build's one minified bundle; the box is disabled, and turning it on refused, when the build made no bundle |
| Run cron every | how often page requests start automated cron |

A site that has not saved these takes the first two from `springdrop.cache.page-max-age`
and `springdrop.assets.aggregate`. "Clear all caches" empties every cache the site keeps, so
each page, render, and listing is built again when next asked for.

## Logging and errors

`/admin/config/development/logging` sets, in `system.logging`:

| Field | Does |
| --- | --- |
| Error messages to display | what a server error's page shows, to everyone who meets it: nothing beyond that something went wrong, the error's message, or its message and stack trace |
| Database log messages to keep | how many events the database log keeps, all or 100 to 1,000,000; cron removes the oldest beyond that |
| Write events to | the log backends in use |

## File system

`/admin/config/media/file-system` shows where public, private, and temporary files are
kept, which `springdrop.files.public-path`, `springdrop.files.private-path`, and the Java
runtime set, and sets the default download method in `system.file`: public files served by
the web server at `/files`, or private files served through access checks. A new upload is
kept in the scheme its field's storage names, or in the default download method when it
names none, so switching the method moves new uploads and leaves files already stored where
they are.

## Maintenance mode

`/admin/config/development/maintenance` puts the site into maintenance mode, with the
message visitors see meanwhile, in `system.maintenance`. A site that has not saved them
follows `springdrop.maintenance.enabled` and `springdrop.maintenance.message`. The message
is required while maintenance mode is on.

In maintenance mode every request is answered with a 503 and the message, except the
actuator, the error pages, and signing in and out, and except for the first account and
anyone holding `access site in maintenance mode`. The gate runs after the security chain,
so it knows who is asking.
