# Comments

Comments are what readers write about an entity. An entity takes them through a comment
field, and each comment sits in a thread under the entity or under the comment it replies
to.

## The comment entity

`comment` is a fieldable content entity, labeled by its subject. Its base fields are:

| Field | Holds |
| --- | --- |
| `entity_type`, `entity_id`, `field_name` | what the comment is about, and the field it was posted through |
| `pid` | the comment it replies to, `0` for a top-level comment |
| `comment_body` | what it says, as plain text |
| `owner` | the account that wrote it, `0` for someone not signed in |
| `name`, `mail`, `homepage` | the contact details someone not signed in left |
| `hostname` | the address it was posted from |
| `created`, `changed` | when it was posted and last changed |
| `status` | whether it is published |
| `thread` | where it sorts in its thread |

`CommentService.install` creates the comment tables when the application starts. A
comment's thread position has one segment per level, each a length-prefixed base-36
number, joined by dots and ended with a slash: `00/`, `00.00/`, `01/`. A new top-level
comment comes after the entity's last, and a reply after the last reply under the comment
it answers, so sorting by position gives replies under what they reply to. A comment
posted without a subject is given the start of its text, cut at the last whole word within
29 characters.

## The comment field

The `comment` field type stores whether the entity takes comments: open, closed, or
hidden. Its widget offers the three, starting from the field's default value, or open
when it has none. Its instance settings are edited on the Field UI:

| Setting | Means |
| --- | --- |
| `default_mode` | `threaded`, replies indented under what they reply to, or `flat`, every comment in the order posted |
| `anonymous` | whether someone not signed in leaves contact details: `0` none, `1` optionally, `2` required |
| `preview` | `0` no preview, `1` optional, `2` required before saving |
| `depth` | how many levels a thread may reach, `0` for any |

The field's formatter draws the comments under the entity it belongs to: the thread the
person may read, each comment with Reply, Edit, and Delete buttons where they may use
them, and an Add new comment button while comments are open and they may post. A closed
field shows its comments and takes no more. A hidden field draws nothing. Replies are not
offered in a flat thread, or under a comment as deep as the field allows.

## Posting

| Path | Does |
| --- | --- |
| `/comment/reply/{type}/{id}/{field}` | posts a comment about an entity through one of its comment fields |
| `/comment/reply/{type}/{id}/{field}/{comment}` | posts a reply to a comment |
| `/comment/{id}` | leads to the comment's place on the page of what it is about |
| `/comment/{id}/edit`, `/comment/{id}/delete` | change or delete a comment |

Posting is refused unless the entity exists and may be read, the field is one of its
comment fields and is open, and the person may post. A reply has to answer a comment of
the same thread that the person may read and that is not as deep as the field allows.

Someone not signed in leaves the contact details the field asks for. Their name may not
be an account's name, the address has to be well formed, and a homepage has to start
with `http://` or `https://`. Preview shows the comment above the form without saving it.
A field requiring preview offers Save only once the comment was previewed, and refuses a
save that was not.

A comment is published straight away for someone holding `skip comment approval` or
`administer comments`, and otherwise waits for approval. A comment waiting for approval
is shown only to someone who administers comments, marked as unapproved, and cannot be
replied to. An administrator's edit form also sets whether the comment is published.
Deleting a comment deletes its replies.

## Admin

`/admin/content/comment` lists published comments and
`/admin/content/comment/approval` the ones waiting for approval, as tabs, and both require
`administer comments`. Each row shows the subject, the author, what the comment is about,
when it changed, and an Edit button. The ticked rows take one action: unpublish or delete
on the published list, publish or delete on the approval list. The apply button stays
disabled until a row is ticked, and the server refuses a submission with nothing ticked or
an action the list does not offer. Deleting shows the comments it will delete and waits
for the confirmation.

## Access

`CommentAccessHandler` opens a published comment to anyone holding `access comments`. Its
author may change it while holding `edit own comments`. Posting takes `post comments`.
`administer comments` allows everything, including reading comments waiting for approval
and deleting any comment. Who wrote a comment is shown by `CommentAuthors`: the account's
name, or the name someone not signed in left marked as not verified, or Anonymous.
