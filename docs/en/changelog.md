# Changelog

## 1.2.2

- `searchable.hibernate.order-inserts` is off by default. With ordered inserts, an entity that names its parent by an id column, with no insertable association to it, could be written before that parent. Set it to `true` to order inserts as before.

## 1.2.0

- Add `searchable.pattern-matching.case-insensitive` so CONTAINS, STARTS_WITH and ENDS_WITH can ignore case. Off by default.
- Add `@SearchableField(keyword = true)` and the reserved `keyword` query parameter: the text matches a record when any keyword field contains it, AND-combined with the other conditions. A DTO with no keyword field refuses the parameter.
- Apply a condition on a field behind a collection through one `EXISTS` over the entity instead of a join with `SELECT DISTINCT`. Rows and totals are unchanged, and an entity with a large-object column can be searched on SQL Server.
- Add `caseSensitivity`, `match` and `aggregatable` to `@SearchableField`: case-insensitive comparison per field for equality, membership and patterns; conditions matched by some element or by no element of a collection; search through an `@ElementCollection`; fields offered for aggregation.
- Add server scope overloads taking a `Specification` to `SearchableServiceSupport`, `SearchableServiceDelegate`, `SearchAggregator` and `TimeBucketCounter`.
- Add `scrollWithSearch` with keyset or offset positions and `SearchCursor` for positions a client hands back; add `projectWithSearch`, which selects only the columns an interface or record projection reads.
- Add `avg`, `min`/`max` over non-numeric fields, DTO-addressed aggregates and a ceiling on returned values to `SearchAggregator`; add `LocalDateTime`, `OffsetDateTime` and `ZonedDateTime` axes and calendar slices (`countByCalendar`) to `TimeBucketCounter`.
- Resolve a controller parameter `@SearchableParams SearchCondition<D>` from the query string in the starter. `@SearchableParams` is now in `dev.simplecore.searchable.core.annotation`; the one in the OpenAPI module is deprecated and still honored. Add an opt-in problem-detail response for invalid searches.
- Add settings for request limits, the IN batch size, the write chunk size, a required write filter, aggregate values and bucket count. With the starter, the application's `MessageSource` and Spring MVC conversion service are used.
- Accept an optional `PlatformTransactionManager` in `SearchableServiceDelegate` and `DefaultSearchableService`.
- Fix the starter's page and Hibernate settings not reaching the `EntityManagerFactory`; an unknown time zone id now stops startup.
- Fix date handling: a date-only range end covers its whole day on every input path and is no longer rounded into the next day by the database; a date the calendar does not have is refused; text for a `String` field is searched as sent.
- Fix time buckets on a column without an offset that is written outside UTC, `[` in a pattern on SQL Server, and message arguments printed as placeholders.
- Fix OpenAPI output: GET parameters keep their types under OpenAPI 3.1, every schema reference resolves, an operation description written by the application is kept, and `sort` is documented as the one comma-separated value the parser reads.
- `searchable-jpa-core` no longer brings SpringDoc and Spring MVC transitively; the starter still does. A fetch path that names no association is refused. `countWithSearch` and `existsWithSearch` ignore page and size. Removed: `JoinStrategyManager`, `SearchableFieldUtils.extractJoinPaths`, `RelationshipAnalyzer.isValidPath`, `countToManyPaths` and `detectManyToManyFields`, `OpenApiDocUtils.toCamelCase`, and the public `configureHibernateOptimizations` and `configureSearchLimits` methods of `SearchableJpaConfiguration`, whose constructor now takes an `Environment` and a `BeanFactory`.
- The message of a violated DTO constraint follows the locale of the request instead of the default locale of the JVM.
- Korean messages no longer attach a particle directly to a value.
- Rewrite the documentation in the order of adoption. Its Java, HTTP and JSON listings come from files the test suite compiles and runs, and `npm run test:docs` checks the listings, the coverage of features and the parity of English and Korean.

## 1.1.3

The same corrections are maintained on the Boot 4 and Boot 3 lines; each line retains its framework major version.

- Preserve mixed AND/OR groups and singleton group connectors; support inherited DTO properties, aliases and property-level validation.
- Retain Jackson 2 public APIs, add Jackson 3 typed-body support, preserve JSON round trips and classify malformed HTTP search bodies as client errors.
- Apply page limits per persistence unit; bound condition depth, node count, sort count, IN values and JPA offsets.
- Choose direct queries for simple single-key searches; retain two-phase loading for collection joins/composite keys, avoid duplicate counts and split independent collection fetches.
- Traverse writes in chunks, preserve transactions and callbacks, and protect ID/version/audit/non-updatable properties and omitted primitive patch values.
- Count each root once in precise half-open time buckets; retain datasource transaction guarantees.
- Upgrade stable dependencies and Gradle, require normal and performance checks during release, and generate deterministic test fixtures on fresh checkouts.
- Correct version navigation for project subpaths, retain language/page/heading, handle missing pages and failed requests, preserve snapshots on release deployment and refresh the English and Korean documentation.

Existing public service and Jackson 2 signatures remain available. Oversized requests that previously bypassed documented limits are now rejected. See [configuration](./auto-configuration.md), [query strategy](./two-phase-query-optimization.md) and [API behavior](./api-reference.md).


This document records the notable changes to the searchable-jpa library.

> See the linked GitHub commits for the full details of each change.

---

## Recent Changes

### v1.1.1

#### New Features
- **Counting rows per time bucket**
  - `TimeBucketCounter` takes the same `SearchCondition` the list is read with and counts rows per equal-width bucket of a period, in the database
  - The starter registers a `timeBucketCounter` bean automatically (`@ConditionalOnMissingBean`, so a bean you define yourself takes precedence)
  - `EpochMillisFunctionContributor` registers the `epoch_millis` function with a per-database expression; on databases it does not cover, counting falls back to one conditional sum per bucket
  - [Detailed documentation](/en/advanced-features.md)

---

## Changes by Version

### v1.1.0

This release includes breaking changes.

#### Breaking Changes
- **Consolidated exception hierarchy** [`7ac21bf`](https://github.com/simplix-framework/searchable-jpa/commit/7ac21bf)
  - `SearchableValidationException` and `SearchableParseException` now extend only `SearchableException` (a `RuntimeException`)
  - They no longer extend `jakarta.validation.ValidationException`
  - `SearchableException` retains five subclasses: `SearchableConfigurationException`, `SearchableValidationException`, `SearchableOperationException`, `SearchableJoinException`, and `SearchableParseException`
- **Changed evaluation order for nested and()/or() condition groups**
  - Nested `and()`/`or()` groups built with `SearchConditionBuilder` are no longer grouped by operator. Each node is now combined with the accumulated result using its own operator, in call order (left to right).
- **Changed priority of Hibernate auto-configuration**
  - The `searchable.hibernate.*` defaults registered by the starter now apply at the lowest priority, so any Hibernate settings you configure always take precedence.
  - Removed an ineffective `@ConditionalOnProperty` on a `@PostConstruct` method, and added validation requiring batch size properties to be 1 or greater.
- **Changed how timezone-less date/time search values are interpreted**
  - Previously interpreted in the deployment host's JVM default timezone; now interpreted in the application timezone, defaulting to UTC when unset.
  - The timezone is resolved in this order: `searchable.date-time.default-timezone`, an `applicationZoneId` bean from the host, `spring.jackson.time-zone`, then UTC.

#### Improvements
- **Pagination**: resolves `@EmbeddedId` property names from the metamodel, computes the total count correctly even for requests beyond the last page, handles distinct counting for composite keys, stabilizes to-many sorting with GROUP BY aggregation, and separates the logic into `CompositeKeyQueryExecutor` and `SpecificationQuerySupport`
- **Parsing**: applies comma splitting only to multi-value operators such as IN, NOT_IN, and BETWEEN, rejects empty pattern values, and validates the second BETWEEN value
- **Joins and condition building**: adds an ESCAPE clause to LIKE conditions to correctly handle literal `%` and `_` characters, and rejects empty pattern values
- **Service, utilities, and exceptions**: honors inherited fields from `@MappedSuperclass`, and adds support for parsing UUID values
- **Configuration**: adds `searchable.date-time.default-timezone` to set the timezone used to interpret search values
- **OpenAPI**: preserves enum values in IN/NOT_IN/BETWEEN schemas, generates distinct upper and lower bounds for BETWEEN examples, documents inherited fields, guards array-type schema generation against NPEs, and assigns a per-type `format` (date/partial-time/date-time) to date/time parameters

### v1.0.11
- Introduced `SearchableServiceDelegate` and `SearchableServiceSupport` ([`a2aeb3a`](https://github.com/simplix-framework/searchable-jpa/commit/a2aeb3a))
  - Services that cannot extend `DefaultSearchableService` can now use composition instead
  - `SearchableServiceDelegate` handles the search, sort, and pagination logic, while `SearchableServiceSupport` is a mixin interface that wires it in

### v1.0.10
- Downgraded excessive info/debug logs to trace level ([`25c5ec9`](https://github.com/simplix-framework/searchable-jpa/commit/25c5ec9))

### v1.0.9
- Reduced response size by simplifying OpenAPI documentation output ([`bf5211d`](https://github.com/simplix-framework/searchable-jpa/commit/bf5211d))
- Added Context7 documentation indexing configuration ([`a1680ad`](https://github.com/simplix-framework/searchable-jpa/commit/a1680ad))

### v1.0.8
- Fixed an issue where `orXXX()` operators and nested group conditions were dropped ([`4bf103d`](https://github.com/simplix-framework/searchable-jpa/commit/4bf103d))
- Improved performance by adding entity metadata caching ([`39a90db`](https://github.com/simplix-framework/searchable-jpa/commit/39a90db))

### v1.0.7
- Fixed OpenAPI example generation to use fixed date/time values ([`217dd95`](https://github.com/simplix-framework/searchable-jpa/commit/217dd95))
- Removed emojis from logs and documentation ([`35544f1`](https://github.com/simplix-framework/searchable-jpa/commit/35544f1), [`8ef44ca`](https://github.com/simplix-framework/searchable-jpa/commit/8ef44ca))

### v1.0.6
- Added `LocalDate`/`LocalTime` support to `OpenApiDocUtils` ([`ec438bf`](https://github.com/simplix-framework/searchable-jpa/commit/ec438bf))
- Downgraded verbose debug logs to trace level ([`3edbe19`](https://github.com/simplix-framework/searchable-jpa/commit/3edbe19))

### v1.0.5
- Added explicit `fetchFields` support ([`82daca0`](https://github.com/simplix-framework/searchable-jpa/commit/82daca0))
  - Added a `fetchFields` property to `SearchCondition`
  - Lets you explicitly fetch-join lazily loaded relationships
  - Supports nested paths (for example, `author.profile`)
  - Applies `@JsonIgnore` for security, so it can only be set on the server side
  - [Full details](/en/relationship-and-two-phase-query.md#explicit-fetch-join-fetchfields)

### v1.0.4
- Built a Docsify-based Korean documentation site ([`7732a1e`](https://github.com/simplix-framework/searchable-jpa/commit/7732a1e), [`9cbe95f`](https://github.com/simplix-framework/searchable-jpa/commit/9cbe95f), [`1a10831`](https://github.com/simplix-framework/searchable-jpa/commit/1a10831))
- Improved exception handling in the OpenAPI example generation logic ([`c85a582`](https://github.com/simplix-framework/searchable-jpa/commit/c85a582))

### v1.0.3
- Added support for searching JSON-typed fields ([`86fdcb8`](https://github.com/simplix-framework/searchable-jpa/commit/86fdcb8))

### v1.0.2
- Version-number-only release with no other changes

### v1.0.1
- Added support for extending (subclassing) `SearchConditionBuilder` ([`2ae76d0`](https://github.com/simplix-framework/searchable-jpa/commit/2ae76d0))

### v1.0.0
- First stable release
- Supports Spring Boot 3.2.x+
- Compatible with Jakarta EE 9+

---

## Related Links

- [GitHub Repository](https://github.com/simplix-framework/searchable-jpa)
- [Issue Tracker](https://github.com/simplix-framework/searchable-jpa/issues)
- [Full Commit History](https://github.com/simplix-framework/searchable-jpa/commits/master)
