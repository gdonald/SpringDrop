# Workflows

A workflow is the set of states something moves through and the transitions that move
it. It is stored as the config entity `workflows.workflow.<id>`.

## The model

`WorkflowConfig` holds the workflow's id, label, type, states, and transitions:

- `WorkflowState` is a machine name, a label, and a weight.
- `WorkflowTransition` is a machine name, a label, the states it starts from, the state
  it ends in, and a weight.

States and transitions are kept in weight order. `transitionBetween(from, to)` finds the
transition making a move, `transitionsFrom(state)` the ones leaving a state. Removing a
state removes the transitions ending in it and stops the others starting from it, leaving
out any that then start nowhere.

`problems()` lists what makes a workflow unusable: a state or transition without a label,
a transition starting nowhere, one starting or ending in a state the workflow does not
have, and two transitions making the same move.

## Workflow types

A workflow type is a plugin registered with `@SpringDropPlugin(type = WorkflowType.class)`.
It names itself, builds the states and transitions a new workflow of it starts with, and
lists the states every workflow of it has to keep.

| Id | Label | Starts with | Requires |
| --- | --- | --- | --- |
| `content_moderation` | Content moderation | Draft and Published, with Create new draft and Publish | `draft`, `published` |

## Permissions

Every transition is guarded by its own permission, `use <workflow> transition
<transition>`, which `WorkflowPermissions` builds from the workflows the site has.
`WorkflowManager.refusal(workflow, from, to, authentication)` says why someone may not
make a move: no transition makes it, or they do not hold its permission. Nothing comes
back when they may. `usable(workflow, state, authentication)` lists the transitions out
of a state someone may use. The first account may use every transition.

`WorkflowManager.save` refuses a workflow with problems, of a type the site does not
have, or missing a state its type requires, and answers with what is wrong.

## Admin

`/admin/config/workflow/workflows` lists the workflows and requires `administer
workflows`. Adding a workflow asks for its label and type, and opens it with the states
and transitions its type starts with. A workflow's page renames it, lists its states and
transitions with the permission guarding each transition, and adds, changes, and deletes
them. Machine names come from labels. A state the workflow's type requires has no Delete
button and cannot be deleted. A change that would leave the workflow with a problem comes
back on its form with the problem and is not saved.

## Content moderation

`ContentModerationService.installEditorial()` adds the Editorial workflow: Draft,
Published, and Archived, with Create new draft, Publish, Archive, Restore to Draft, and
Restore. It moderates no content type until one is chosen.

A content moderation workflow's settings are the config object
`content_moderation.workflow.<workflow>`, edited at
`/admin/config/workflow/workflows/manage/<id>/moderation`:

- For each state, whether content in it is published and whether a revision moved into
  it becomes the one the site shows. A published state has to be the default revision,
  which the form requires on both sides. A state with no settings is an unpublished
  draft.
- The content types it moderates. A content type another workflow moderates is shown
  disabled and refused on save.

Every save of moderated content is a new revision in the state the node form's
"Change to" choice names. The choice offers the states the person may move the newest
revision into, and the save refuses a move no transition makes or the person may not
use, keeping the form with the reason on the choice. The state sets the node's published
status.

A state that does not make its revision the default saves a forward revision when the
live revision is published: `/node/<id>` keeps showing the published revision, and the
draft waits ahead of it until a published state is saved over it. Content never
published has nothing live to keep, so its drafts are the revision the site shows,
unpublished. The edit form starts from the newest revision.

`/node/<id>/latest` shows the newest revision when it is ahead of the live one, as the
node page's Latest version tab, to someone holding `view latest version` who may edit the
node.

`/admin/content/moderated` lists moderated content by the state its newest revision is
in, everything not published by default, with a filter by state, and requires
`view any unpublished content`. Each row links to the latest version when there is one
ahead of the live revision, and offers Edit to someone who may edit the node.
