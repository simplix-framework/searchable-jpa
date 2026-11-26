# Upgrade guide

Changes in behavior and removed API to check when moving to a newer version. The [Changelog](./changelog.md) holds everything a version changed.

## From 2.1.1 to 2.1.2

### Changed behavior

| Area | 2.1.1 | 2.1.2 |
| --- | --- | --- |
| `searchable.hibernate.order-inserts` | On by default | Off by default. Set it to `true` when every parent is referenced through an association and you want inserts grouped for JDBC batching |

## From 2.0.x to 2.1.0

### Changed behavior

| Area | 2.0.x | 2.1.0 |
| --- | --- | --- |
| Starter settings | Page sizes and Hibernate defaults did not reach the `EntityManagerFactory` | They are handed over and apply. Check that the values you set behave as intended |
| Time zone ID | An invalid ID fell through to the next source | It stops the start of the application |
| Dates the calendar does not have | Searched as a nearby day | Refused |
| End of a range written with dates only | JSON and the builder included the last date up to midnight only | Every input form includes the whole last date |
| Text values of GET | Trimmed and normalized, and the text `null` was read as no value | Searched as sent |
| A request with `size` only | Refused | Reads page 0 |
| SQL of a collection condition | A join with `SELECT DISTINCT` | An `EXISTS`. Rows and totals are unchanged |
| A fetch path that is no association | Ignored | Raises `SearchableJoinException` |
| `countWithSearch`, `existsWithSearch` | Validated the page and the size of the condition | Do not validate them |
| Invalid input of aggregates and time buckets | Raised `IllegalArgumentException` or an exception of the JPA provider | Raises `SearchableValidationException` |
| Count per value | Returned every value | Returns up to `searchable.aggregate.max-groups` values, the most frequent first |
| Sort order of an `@IdClass` key | The order the metamodel returned | The order the identifier class declares |
| Language of a violated DTO constraint | The default locale of the JVM | The locale of the request |
| Messages in the Korean locale | Limit, service and web messages were English | The Korean texts of the bundle are used |
| enum parameters in OpenAPI | Written as a schema reference | The list of values is written into the parameter |
| `sort` in OpenAPI | Written as an array of repeated parameters | Written as one comma-separated value |
| Transitive dependencies of `searchable-jpa-core` | Included SpringDoc and Spring MVC | Not included. The starter still includes them |

### Removed API

| Removed | Use instead |
| --- | --- |
| `JoinStrategyManager` | Nothing. Joins follow from conditions and sorts |
| `SearchableFieldUtils.extractJoinPaths` | Nothing |
| `isValidPath`, `countToManyPaths`, `detectManyToManyFields` of `RelationshipAnalyzer` | Nothing. They were internal analysis methods |
| `OpenApiDocUtils.toCamelCase` | Nothing |
| `configureHibernateOptimizations`, `configureSearchLimits` and the former constructor of `SearchableJpaConfiguration` | Use the auto-configuration of the starter as it is |

### Deprecated

| Item | Note |
| --- | --- |
| `searchable.hibernate.batch-versioned-data` | Hibernate 7 does not read the setting. Remove it from your configuration |
| `dev.simplecore.searchable.openapi.annotation.SearchableParams` | Change to `dev.simplecore.searchable.core.annotation.SearchableParams`. The former annotation still works |

### Features added

[Server scope](./server-scope-and-limits.md), [scrolling and column projections](./reading-results.md), [ways of matching a collection](./relationship-and-two-phase-query.md), [keyword search and case handling per field](./search-fields.md), [conversion of controller parameters](./request-formats.md), [the 400 answer and message replacement](./errors-and-messages.md), [aggregates](./aggregates.md) and [calendar buckets](./time-buckets.md) were added.

## From 1.x to 2.x

2.x is for the Spring Boot 4 line only. Move the application to Boot 4 first, then the library to 2.x. Service inheritance, the construction of a delegate, query parameter names and the Jackson 2 method signatures are kept. Requests beyond the documented limits are refused in 2.x.

## 0.1.x

0.1.x uses Spring Boot 2.7 and the `javax.*` packages and is no longer maintained.
