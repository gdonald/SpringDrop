# Updates

## Update routines

An `UpdateRoutine` bean is one change a module makes to a site that already has it, to its
schema or its data, the way Drupal's `hook_update_N` does. It names its module, `system`
for core, a version such as 9001, a description, and the work it does.

`UpdateManager` keeps the last version applied for each module in the `module_update`
table. A routine is pending when its module, or core, is installed and its version is above
that. Routines run core first, then each module in the order modules install in, each in
version order, every one in its own transaction and recorded as soon as it succeeds. The site
is in maintenance mode while they run and is put back as it was after. The run stops at the
first routine that fails, which stays pending with those after it.

A site never runs the routines that came before it: installing a module records its routines
as applied, and the installer records every routine as applied when it finishes.

## Running updates

| Way | Does |
| --- | --- |
| `/update` | lists the pending routines with "Apply pending updates", which runs them and reports how many were applied and which failed, behind `administer software updates` |
| starting the site with `--update` | runs the pending routines once the site has started, and logs what was applied and what failed |
