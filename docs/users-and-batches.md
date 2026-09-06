# Accounts and batches

## Accounts

The user entity type is a content entity like any other, so a site adds fields to
accounts the same way it adds them to anything else. It has no bundles, so its fields
hang on the type's own name and every account carries the same set. What an account
always has is defined in code: its name and mail address, the hash of its password,
whether it is active, when it was created, when it was last seen and last signed in,
and its timezone and preferred language.

`UserAccountService.install()` creates the account storage, its unique indexes, and the
two accounts a site always has: anonymous, which is id 0 and never signs in, and the
first account, id 1. A name or a mail address belongs to one account whatever case it
was typed in, which unique `lower()` indexes enforce and the service reports as a
`DuplicateAccountException` rather than a database error.

The first account bypasses permission checks, so a site can always be recovered by its
owner. `RouteAccessChecker` grants it any route; every other account, signed in or not,
needs the permission.

## Signing in

`AccountDetailsService` finds the account behind a name, and a blocked account is loaded
as disabled rather than hidden, so a refused sign-in is refused for what it is.
Passwords are hashed with BCrypt. The security chain serves the sign-in form at
`/user/login` and sign-out at `/user/logout`, starts a new session on sign-in so a
session id handed out beforehand cannot be used after, and offers a remember-me cookie
good for two weeks.

## Batches

Long work runs a chunk at a time, so no single request has to carry all of it. A batch
names its operations rather than carrying code, since it is stored between requests:

```java
batches.start(BatchDefinition.of("rebuild", List.of(
                BatchOperationSpec.of("reindex_node", Map.of("id", 1)),
                BatchOperationSpec.of("reindex_node", Map.of("id", 2))))
        .inChunksOf(20)
        .finishingAt("/admin/content"));
```

Each `BatchOperationHandler` is a bean named by its id. `processChunk` carries out the
next chunk and reports where the batch stands; an operation that throws is recorded
against the batch and the rest carry on. A finished batch runs nothing more however
often it is asked to.

`/batch/{id}` watches one run: a progress bar that asks for the next chunk as it loads
and after each one, and a plain button that does the same for a browser without
scripting. When the batch finishes, the bar stops asking and points onward.

## Signing up and getting back in

`user.settings` decides what a visitor may do on their own: sign up and use the account
at once, sign up and wait for an administrator, or nothing at all. A site that checks
addresses leaves a new account closed and sends a one-time link to open it.

`OneTimeLinkService` is behind both that link and the password reset: a token stands for
one account and one purpose, lapses on its own, and is spent when it is used, so a link
forwarded to somebody else has nothing left to give. A reset link signs the person in
once so they can set a new password. Asking for a link says the same thing whoever is
asked about, so the form cannot be used to find out who has an account here.

## Permissions and roles

Modules declare permissions in `springdrop/permissions/*.permissions.yml`, keyed by the
permission name, with a title, a description, and whether the permission reaches past
the site's own rules:

```yaml
administer fields:
  title: Administer fields
  description: Add, change, and remove fields, and lay out forms and displays.
  restricted: true
```

`PermissionRegistry` gathers them, along with any a module registers as a
`PermissionProvider` bean, and the roles admin marks the restricted ones.

A role is a config entity holding a set of permissions. Two roles always exist:
anonymous, held by everyone who has not signed in, and authenticated, held by everyone
who has. A role marked as the administrator role carries every permission the site
knows about, including ones added later, so nobody has to go back and tick a new box.
Signing in expands the account's roles into the permissions the access layer checks.

`/admin/people/permissions` is the matrix: a row per permission, a column per role, and
a checkbox where they meet. Ticking a box and saving gates the matching route at once;
an administrator role's boxes are ticked and disabled, since there is nothing to decide.

## Deciding access

`AccessResult` is allowed, forbidden, or neutral, with a reason and the
cacheability of the decision. Neutral is what a rule says when it has no opinion, so
several modules can be asked in turn: `or` lets any of them allow, `and` needs them all,
and one forbidding settles it either way.

`EntityAccessManager` asks the entity type's own handler first, then every
`EntityAccessRule` bean, for `view`, `update`, `delete`, and `create`. The default
handler allows whoever holds `administer <entity type>` and says nothing about anyone
else, so a type with no rules of its own is closed to all but its administrators while a
module rule can open it. The first account is not asked about at all.

The same rules reach three places. A controller asks the manager; a listing asks by
tagging its query, which `EntityQueryAccessFilter` narrows through the matching
`EntityQueryAccessRule`, so a page of results is a page the reader can actually open;
and a template asks `${@access.mayUpdate('node', node)}`, so an Edit button is left out
rather than shown and then refused.

## People

`/admin/people` lists accounts, narrowed by name, status, or role, with Edit and Close
as buttons. Editing assigns roles; closing asks first and says what becomes of anything
the person wrote: block the account, block it and unpublish their content, delete the
account and credit their content to Anonymous, or delete both. Content is found by its
owner base field, so any content type recording an owner is covered.

## Flood control

`FloodService` counts how often something has been tried lately, per identifier, with
the count fading as its window passes. Sign-ins are counted per account and per address:
past the threshold an account is refused before its password is looked at, and signing
in successfully forgets the failures before it. Password reset requests are counted too,
so the form cannot be used to bury someone in mail.
