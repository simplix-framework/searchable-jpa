# API reference

The public types and methods an application uses. How to use them is on the guide page named with each group. Internal classes such as the query executors are not an extension contract and can change between versions.

## Annotations

Package: `dev.simplecore.searchable.core.annotation`. Guide: [Declaring search fields](./search-fields.md).

| `@SearchableField` attribute | Type | Default | Meaning |
| --- | --- | --- | --- |
| `entityField` | `String` | `""` | The entity path that is searched. Empty uses the DTO field name |
| `operators` | `SearchOperator[]` | `{}` | The operators allowed. Empty allows every operator |
| `sortable` | `boolean` | `false` | Whether the field can be sorted by |
| `sortField` | `String` | `""` | The sort path. Empty uses `entityField`, then the DTO field name |
| `keyword` | `boolean` | `false` | Whether the `keyword` parameter searches the field |
| `caseSensitivity` | `CaseSensitivity` | `DEFAULT` | `DEFAULT`, `INSENSITIVE`, `SENSITIVE` |
| `match` | `CollectionMatch` | `JOINED` | `JOINED`, `ANY`, `NONE` |
| `aggregatable` | `boolean` | `false` | Whether an aggregate addressed by the DTO field is allowed |

`@SearchableParams` is declared on a controller parameter. Its `value()` is the search DTO class and can be left out on a `SearchCondition<D>` parameter. The annotation of the same name in `dev.simplecore.searchable.openapi.annotation` is deprecated.

## SearchCondition

Package: `dev.simplecore.searchable.core.condition`. Guide: [Writing requests](./request-formats.md).

| API | Behavior |
| --- | --- |
| `new SearchCondition<>()` | A search without conditions. No page is set |
| `getNodes()` | The ordered list of conditions and groups. The JSON property is `conditions` |
| `getPage()` / `setPage(Integer)` | The page number, starting at 0 |
| `getSize()` / `setSize(Integer)` | The page size |
| `getSort()` / `setSort(Sort)` | The sort |
| `getFetchFields()` / `setFetchFields(Set<String>)` | Fetch paths set by the server. Not read from JSON |
| `fromJson(String, Class<D>)` | Converts a JSON string and validates it against the DTO |
| `toJson()` | Converts to a JSON string |

`fromJson` and `toJson` declare the `JsonProcessingException` of Jackson 2. Server code that builds a condition directly uses the nested types below. A condition built this way passes no DTO validation, so the builder is recommended.

| Nested type | Construction | Note |
| --- | --- | --- |
| `Node` | none | The common type of conditions and groups. `getOperator()`, `setOperator(LogicalOperator)` |
| `Condition` | `(LogicalOperator, String field, SearchOperator, Object value, Object value2, String entityField)` | A condition |
| `Group` | `()`, `(LogicalOperator, List<Node>)` | An ordered list of child nodes |
| `Sort` | `()` | `addOrder(Order)` adds an order |
| `Order` | `(String field, Direction)`, `(String field, Direction, String entityField)` | A sort entry |
| `Direction` | `ASC`, `DESC` | The JSON values are `asc`, `desc` |

`LogicalOperator` is `AND` or `OR`, with the JSON values `and` and `or`. `new SearchCondition<>(nodes, page, size)` sets the node list and the page together.

## Builder

Guides: [Writing requests](./request-formats.md), [Operators and values](./search-operators.md).

| `SearchConditionBuilder` | Purpose |
| --- | --- |
| `create(DTO.class)` | Starts a builder that validates against the DTO |
| `from(existing, DTO.class)` | Copies an existing condition to extend it |
| `where(...)` | The first group of conditions |
| `and(...)` / `or(...)` | Combines a group with the whole expression so far |
| `sort(...)` | Sets the sort with `asc(field)` and `desc(field)` of `SortBuilder` |
| `page(int)` / `size(int)` | Sets the page |
| `fetchFields(String...)` / `fetchFields(Set<String>)` | Adds fetch paths |
| `build()` | Validates the structure and the DTO declaration and returns the condition |

Condition methods used inside a group:

| Combination | Methods |
| --- | --- |
| First condition, AND | `where`, `equals`, `notEquals`, `greaterThan`, `greaterThanOrEqualTo`, `lessThan`, `lessThanOrEqualTo`, `contains`, `notContains`, `startsWith`, `notStartsWith`, `endsWith`, `notEndsWith`, `isNull`, `isNotNull`, `in`, `notIn`, `between`, `notBetween` |
| OR | `orEquals`, `orNotEquals`, `orGreaterThan`, `orGreaterThanOrEqualTo`, `orLessThan`, `orLessThanOrEqualTo`, `orContains`, `orNotContains`, `orStartsWith`, `orNotStartsWith`, `orEndsWith`, `orNotEndsWith`, `orIsNull`, `orIsNotNull`, `orIn`, `orNotIn`, `orBetween`, `orNotBetween` |
| Nested group | `and(...)`, `or(...)` |

Range methods take two bounds, list methods a `List`, and the null checks a field name only. The lambdas of `where`, `and` and `or` receive a `ConditionGroupBuilder`. The condition methods are declared on the interfaces `FirstCondition` and `ChainedCondition`, and the sort, page and fetch methods return a `ChainedSearchCondition<D>`, so calls can be chained.

## Parameter parser

`new SearchableParamsParser<>(DTO.class).convert(Map<String, String>)` converts query parameters into a `SearchCondition`. With `true` as the second constructor argument, a name that is not a search parameter is left out. The reserved parameter names are `sort`, `page`, `size` and `keyword`; `keyword` is available as the constant `SearchableParamsParser.KEYWORD_PARAM`.

## Services

Package: `dev.simplecore.searchable.core.service`. Guides: [Ways to read](./reading-results.md), [Bulk update and delete](./bulk-write.md).

| Method | Returns | Meaning |
| --- | --- | --- |
| `findAllWithSearch(condition)` | `Page<T>` | A page and the total |
| `findAllWithSearch(condition, type)` | `Page<P>` | An entity-backed interface projection |
| `findOneWithSearch(condition)` | `Optional<T>` | The only matching row. Fails when two or more match |
| `findFirstWithSearch(condition)` | `Optional<T>` | The first row of the sort |
| `countWithSearch(condition)` | `long` | The count |
| `existsWithSearch(condition)` | `boolean` | Whether a row exists |
| `scrollWithSearch(condition, position)` | `Window<T>` | One window |
| `scrollPosition(cursor)` | `ScrollPosition` | Turns cursor text into a position. Null or blank is the first position |
| `projectWithSearch(condition, type)` | `Page<P>` | A column projection |
| `updateWithSearch(condition, patch)` | `long` | Updates every row the condition matches |
| `deleteWithSearch(condition)` | `long` | Deletes every row the condition matches |

Every method except `scrollPosition` has an overload that takes a `Specification<T>` scope after the condition. The interface `SearchableService<T>` declares only the reads, counts, updates and deletes without a scope. The scope overloads, scrolling and column projections are on `SearchableServiceSupport` and `SearchableServiceDelegate`.

| Type | Construction |
| --- | --- |
| `DefaultSearchableService<T, ID>` | `(JpaRepository, EntityManager)`, optionally a `PlatformTransactionManager` |
| `SearchableServiceDelegate<T, ID>` | `(JpaRepository, EntityManager, Class<T>)`, optionally a `PlatformTransactionManager` |
| `SearchableServiceSupport<T, ID>` | Implementing `getSearchableDelegate()` provides the methods above as default methods |

`SearchCursor.encode(ScrollPosition)` turns a scroll position into text that fits a URL, and `SearchCursor.decode(String, EntityManager, Class<?>)` reads it back.

## Aggregates

Type: `SearchAggregator`. Package: `dev.simplecore.searchable.core.service.aggregate`. Guide: [Aggregates](./aggregates.md).

| API | Returns | Meaning |
| --- | --- | --- |
| `countByValue(entityType, condition, scope, field)` | `List<ValueCount>` | The count per value |
| `aggregate(entityType, condition, scope, aggregates)` | `List<AggregateValue>` | One figure per `Aggregate(field, op)` |
| Both methods with a DTO class before the field | The same | DTO field names, only `aggregatable` fields |

`Op` is `SUM`, `AVG`, `COUNT`, `COUNT_DISTINCT`, `MIN` or `MAX`, with the names `sum`, `avg`, `count`, `countDistinct`, `min` and `max`. `ValueCount` gives `value` and `count`; `AggregateValue` gives `field`, `op`, `value` and `result`.

## Time buckets

Types: `TimeBucketCounter`, `TimeWindowParams`, `EpochMillisFunctionContributor`. Package: `dev.simplecore.searchable.core.service.bucket`. Guide: [Time buckets](./time-buckets.md).

| API | Returns | Meaning |
| --- | --- | --- |
| `count(entityType, condition, scope, field, from, to, buckets)` | `List<Long>` | The count per equal bucket |
| `countByCalendar(entityType, condition, scope, field, from, to, unit, zone)` | `List<TimeBucket>` | The count per calendar bucket |
| `TimeWindowParams.strip(params)` | `Map<String, String>` | The search parameters without the window parameters `from`, `to` and `buckets` |

`TimeBucket` gives `from`, `to` and `count`. `count` also keeps the earlier overload that takes a repository. `EpochMillisFunctionContributor` registers the `epoch_millis` function with Hibernate and is not called by an application.

## Exceptions

Package: `dev.simplecore.searchable.core.exception`. Guide: [Errors and messages](./errors-and-messages.md).

`SearchableException` (the base type), `SearchableValidationException`, `SearchableParseException`, `SearchableConfigurationException`, `SearchableJoinException`, `SearchableOperationException`.

## Configuration support types

Guide: [Extension and integration](./advanced-features.md).

| Type | Function |
| --- | --- |
| `SearchRequestLimits` | `setStructureLimits(Structure)` sets the structural request limits |
| `SearchableTimeZoneHolder` | `setZoneId(ZoneId)` sets the time zone |
| `SearchablePatternMatchingHolder` | `setCaseInsensitive(boolean)` sets the case of patterns |
| `SearchableConversionHolder` | `setConversionService(ConversionService)` connects the value conversion |
| `MessageUtils` | `init(MessageSource)` connects messages, `getMessage` reads one |
| `SearchableJpaSettings` | The key constants of the persistence unit properties |
| `SearchableFieldUtils` | Metadata of DTO fields and entity keys, `clearCache()` |
| `SearchableValueParser` | `parseValue(String, Class<?>)` and `parseValueForBetween(String, Class<?>, boolean)` convert text to a field type |
| `SearchConditionValidator`, `SearchableFieldValidator` | The validators the builder and the parser use |

## Starter and OpenAPI modules

Guides: [Settings](./auto-configuration.md), [OpenAPI](./openapi-integration.md).

| Type | Role |
| --- | --- |
| `SearchableProperties` | The properties class that reads the `searchable.*` settings |
| `SearchableJpaConfiguration` | The auto-configuration of the time zone, the request limits, the message and conversion wiring and the aggregate beans |
| `SearchableHibernatePropertiesCustomizer` | Hands the persistence unit settings and the Hibernate defaults to the `EntityManagerFactory` |
| `SearchableWebConfiguration` | The auto-configuration that registers the parameter resolver and the 400 handler |
| `SearchConditionArgumentResolver` | Resolves a `@SearchableParams SearchCondition<D>` parameter |
| `SearchableExceptionHandler` | The handler that answers an invalid search with 400 |
| `SearchableOpenApiConfiguration` | The auto-configuration that registers the `searchConditionCustomizer` bean |
| `OpenApiDocCustomiser` | The `OperationCustomizer` that documents search parameters |
