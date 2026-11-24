# Troubleshooting

What to check, by symptom. The background of each entry is on the linked guide page.

## Dependency and startup failures

| Symptom | What to check |
| --- | --- |
| A class is not found, or the start fails | Use 2.x with Boot 4 and 1.x with Boot 3, and one version for every module. `./gradlew dependencyInsight --dependency hibernate-core --configuration runtimeClasspath` shows whether a version was changed |
| GitHub Packages authentication fails | Check the repository URL, the user name and that the token may read packages |
| A locally installed version is not found | Install every module with `./gradlew publishToMavenLocal` and check that `mavenLocal()` was added |
| The auto-configuration does not apply | Check that the starter is on the runtime classpath and `SearchableJpaConfiguration` is not excluded. `debug=true` prints the condition evaluation report |
| Creating the service fails | Check that the repository extends both `JpaRepository` and `JpaSpecificationExecutor` |
| The start fails with a dialect class error | Let Hibernate detect the dialect from JDBC metadata instead of naming one. Dialect class names of older Hibernate versions do not work |
| The start stops with a time zone error | Check the values of `searchable.date-time.default-timezone` and `spring.jackson.time-zone` |

## A request is refused

| Symptom | What to check |
| --- | --- |
| A field is not found | Check that the parameter name is a DTO field name and that the field carries `@SearchableField`. [Declaring search fields](./search-fields.md) |
| An operator cannot be used | Check that `operators` lists it and that the name is exact, as in `greaterThanOrEqualTo`. [Operators and values](./search-operators.md) |
| A sort is refused | Check `sortable = true` and the form `sort=createdAt.desc` |
| A date value is refused | Check the format and that the date exists. Send a time as well for a `LocalDateTime` field |
| `between` is refused | It needs two values in order |
| An unknown parameter is refused | Remove the parameters of the endpoint before calling the parser, or use `searchable.params.ignore-unknown`. [Writing requests](./request-formats.md) |
| A page size or the number of conditions is exceeded | See the limits in [Server scope and limits](./server-scope-and-limits.md) |
| The answer is 500 instead of 400 | Turn on `searchable.web.problem-details.enabled` or write an exception handler. [Errors and messages](./errors-and-messages.md) |

## AND and OR give an unexpected result

Conditions are combined from the left in the order they are declared. `A, or B, and C` is `(A OR B) AND C`. For `A OR (B AND C)`, put B and C into a group. The connector of a group applies also to a group with one entry. See the figure in [Writing requests](./request-formats.md).

## A search is slow or runs much SQL

| Symptom | What to check |
| --- | --- |
| One request runs several SQL statements | A sort or a fetch through a collection is run as a key query and an entity query, and each collection adds a query. [Query strategy and performance](./two-phase-query-optimization.md) |
| Collections that are not needed are loaded | Set `searchable.hibernate.auto-fetch-collections=false` and name the paths you need with `fetchFields` |
| Late pages are slow | Use the keyset scroll. [Ways to read](./reading-results.md) |
| A `contains` search is slow | A pattern with an open start is not served by an ordinary index |
| No count query runs | When fewer rows than the page size are read, the total is computed |

`org.hibernate.SQL=DEBUG` shows the SQL that runs and `org.hibernate.orm.jdbc.bind=TRACE` the bound values.

## Composite keys, projections, updates

| Symptom | What to check |
| --- | --- |
| A field of an `@EmbeddedId` is not searched | Declare `entityField` by the real path, such as `id.tenantId` |
| A keyset scroll is refused | An entity with an `@EmbeddedId` supports offset scrolling only |
| A projection type is refused | The projection of `findAllWithSearch` takes an interface only; `projectWithSearch` takes an interface or a record |
| A projection still selects every column | The projection of `findAllWithSearch` loads entities. Use `projectWithSearch` |
| An update does not change a value | Null attributes of the patch, identifiers, versions, audit fields and `updatable=false` columns are left unchanged. [Bulk update and delete](./bulk-write.md) |
| An update overwrites with 0 or false | Change the primitive attributes of the patch object to reference types |

## Time buckets and JSON

| Symptom | What to check |
| --- | --- |
| The bucket counts do not add up to the list total | A bucket excludes its end, and rows whose time is null are not counted. Check the period and the time axis. [Time buckets](./time-buckets.md) |
| The buckets of a `LocalDateTime` axis are shifted | The value is read in the zone of the application. See the order of the time zone in [Settings](./auto-configuration.md) |
| A JSON body is not converted | Name the type argument on the controller parameter, as in `SearchCondition<MySearchDto>` |

## Documentation version selector

The version selector keeps the language, the page and the heading when the target version has the same page. Otherwise it goes to the start page of that version in the same language, and failing that to the English start page. A network failure keeps the current page. A local preview without version directories shows no selector.
