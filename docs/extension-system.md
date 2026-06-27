# Extension system

How modules register and discover services in SpringDrop. This document grows as
the extension system does.

## Service collection

To gather every implementation of a contract, depend on `ServiceCollector` and call
`collect(Contract.class)`. It returns all matching beans ordered by `@Order` or the
`Ordered` interface, the way Drupal's tagged service collectors gather handlers.

```java
List<FieldType> fieldTypes = serviceCollector.collect(FieldType.class);
```

A module contributes an implementation by registering it as a bean (annotated
`@Component` or declared in a `@Configuration`). Discovery is automatic; no central
registry edit is needed.

## Bean overrides

A module replaces a core service by declaring a bean of the same type marked
`@Primary`, or by providing one that the core service annotates as conditional on its
absence (`@ConditionalOnMissingBean`). Core ships `DefaultMaintenanceBypass`; the
access system will provide a permission-backed `MaintenanceBypass` marked `@Primary`
that supersedes it once roles exist.

## Hook-to-event mapping

The event bus is Spring's `ApplicationEventPublisher`. A module reacts to an event
with `@EventListener` and orders its handlers with `@Order`; several modules can
listen to the same event. Drupal hooks map to events as follows.

| Drupal hook | SpringDrop event |
| --- | --- |
| `hook_entity_presave` | `EntityEvent` with phase `PRESAVE` |
| `hook_ENTITY_TYPE_insert` | `EntityEvent` with phase `INSERT` |
| `hook_entity_update` | `EntityEvent` with phase `UPDATE` |
| `hook_entity_delete` | `EntityEvent` with phase `DELETE` |
| `hook_entity_load` | `EntityEvent` with phase `LOAD` |
| `hook_form_alter` | a `FormAlterEvent extends AlterEvent` (lands with the Form API) |
| `hook_query_alter` | a `QueryAlterEvent extends AlterEvent` (lands with the entity query) |
| `hook_entity_access` | an access `AlterEvent` (lands with the access system) |

### Alter events

Alter hooks become `AlterEvent` subclasses. The event carries a mutable subject (a
form, a query's conditions, a render array); listeners modify it in place, and the
caller uses the mutated subject after publishing. Concrete alter events are added by
the subsystem that owns the subject.
