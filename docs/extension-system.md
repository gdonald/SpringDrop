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

The event bus replaces Drupal hooks. The mapping table lands with the event-bus work
and is maintained here.
