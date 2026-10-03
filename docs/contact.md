# Contact forms

Contact forms let people send the site, or one another, a message without seeing an
email address.

## Forms and messages

A contact form is the config entity `contact_form.<id>`, read and written through
`ContactFormManager`. `ContactForm` holds its label, its recipients, the automatic reply
the sender is sent, the path the sender lands on afterwards, its weight, and whether it is
the form `/contact` shows. Saving a form as the one `/contact` shows stops any other form
being it. The `personal` form, added as the application starts, carries the messages
people send one another, and has no recipients of its own.

A message is the content entity `contact_message`, bundled by contact form, so each
form's messages carry the fields managed for it on the Field UI. Its label is the subject,
and its base fields are the sender's name and address, the message, whether the sender
asked for a copy, the account a personal message was for, the address it was sent from,
the sending account, and when it was sent.

## Sending

| Path | Does |
| --- | --- |
| `/contact` | the selected form, or else the first by weight and label |
| `/contact/{form}` | sends a message through a site-wide form, with `access site-wide contact form` |
| `/user/{id}/contact` | sends a message to one account, with `access user contact forms` |

Someone not signed in gives a name and a well formed address. Someone signed in sends as
their account and may ask for a copy. `ContactService` keeps each message, then mails it:

- Through a site-wide form, to each recipient, with the sender's address to reply to,
  then the form's automatic reply to the sender when it has one.
- Through the personal form, to the account it is for, with the site's own address to
  reply to, so the sender's address is not in the mail.

The sender's text travels as written, never through token replacement. A copy goes to a
sender who asked for one. Each sender may send five messages an hour, counted per account,
or per address for someone not signed in. Past that the form comes back saying so and
nothing is sent, unless the sender holds `administer contact forms`.

A personal message is refused for an account that turned its contact form off, for a
blocked account, and for the anonymous account.

## Admin

`/admin/structure/contact` lists the forms with Edit, Manage fields, and Delete buttons,
and requires `administer contact forms`. A site-wide form's recipients are one or more
addresses separated by commas, and its redirect is a path on this site starting with `/`,
both checked on both sides. The personal form sets only its label and automatic reply, and
cannot be deleted.
