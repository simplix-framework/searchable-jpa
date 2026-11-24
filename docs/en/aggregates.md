# Aggregates

After this page you can compute counts per value, sums, averages, minimums and maximums in the database under the same search condition as a list. Conditions and scopes are those of the pages up to [Server scope and limits](./server-scope-and-limits.md).

## SearchAggregator

The starter registers a `SearchAggregator` bean to inject. Without the starter, create one with `new SearchAggregator(entityManager)`. Every method takes the entity type, the search condition and a server scope, or `null` when there is none.

## Count per value

<!-- snippet: PostStatsController.java#per-value -->
```java
@GetMapping("/stats/status")
public List<ValueCount> perStatus(@SearchableParams SearchCondition<PostSearchDTO> condition) {
    return aggregator.countByValue(Post.class, condition, null, PostSearchDTO.class, "status");
}
```

<!-- snippet: stats-status.http -->
```http
GET /api/posts/stats/status
```

<!-- snippet: stats-status.json -->
```json
[
  {"value": "DRAFT", "count": 1},
  {"value": "PUBLISHED", "count": 2},
  {"value": "ARCHIVED", "count": 1}
]
```

- Each value is given the number of matching root entities. A row counts once however many elements of a collection match.
- An enum field returns every constant in declaration order, including those with a count of 0. Other types return the values that occur, the most frequent first.
- At most `searchable.aggregate.max-groups` values are returned (1,000 by default).
- The result is a list of `ValueCount(value, count)`.

## Sums and averages

<!-- snippet: PostStatsController.java#figures -->
```java
@GetMapping("/stats/views")
public List<AggregateValue> viewFigures(@SearchableParams SearchCondition<PostSearchDTO> condition) {
    return aggregator.aggregate(Post.class, condition, null, PostSearchDTO.class,
            List.of(new Aggregate("viewCount", Op.SUM), new Aggregate("viewCount", Op.AVG),
                    new Aggregate("viewCount", Op.MAX)));
}
```

<!-- snippet: stats-views.http -->
```http
GET /api/posts/stats/views?status.equals=PUBLISHED
```

<!-- snippet: stats-views.json -->
```json
[
  {"field": "viewCount", "op": "sum", "value": 600, "result": 600},
  {"field": "viewCount", "op": "avg", "value": 300.0, "result": 300.0},
  {"field": "viewCount", "op": "max", "value": 480, "result": 480}
]
```

Several figures are computed by one query. The request is a list of `Aggregate(field, op)` and the result a list of `AggregateValue(field, op, value, result)` in the same order. `value` is a numeric result, and `result` also carries results that are not numbers, such as dates.

| Operation | Name | Applies to |
| --- | --- | --- |
| `Op.SUM` | `sum` | Numbers |
| `Op.AVG` | `avg` | Numbers |
| `Op.COUNT` | `count` | Every type |
| `Op.COUNT_DISTINCT` | `countDistinct` | Every type |
| `Op.MIN` | `min` | Numbers, dates and other types with an order |
| `Op.MAX` | `max` | Numbers, dates and other types with an order |

The name is the text `Op.parse` reads, for an API in which a client names the operation.

## Who names the field

| Call | Field names | Accepted |
| --- | --- | --- |
| With the DTO class | DTO field names | Only fields with `@SearchableField(aggregatable = true)` |
| Without the DTO class | Entity attribute names | The fields server code chose |

The examples above pass the DTO class (`PostSearchDTO.class`), so only `status` and `viewCount`, which are declared `aggregatable`, can be aggregated. Use this form for an API in which a client names the field. A count per value returns the values of the field themselves, so declare a field `aggregatable` only when its values may be shown.

When server code chooses the field, it uses entity attribute names directly.

<!-- snippet: PostStatsController.java#server-figures -->
```java
public List<AggregateValue> newestAndTotal(SearchCondition<PostSearchDTO> condition) {
    return aggregator.aggregate(Post.class, condition, null,
            List.of(new Aggregate("createdAt", Op.MAX), new Aggregate("id", Op.COUNT)));
}
```

The method returns the latest creation time (the `result` is an `Instant`) and the number of rows.

## Refused requests

| Request | Result |
| --- | --- |
| A DTO field that is not `aggregatable` | `SearchableValidationException` |
| A field the entity does not have, a collection field | `SearchableValidationException` |
| An operation that does not fit the type (`sum` of an enum) | `SearchableValidationException` |
| An unknown operation name | `SearchableValidationException` |

## Related settings

| Setting | Default | Meaning |
| --- | --- | --- |
| `searchable.aggregate.max-groups` | 1000 | The most values a count per value returns |

## Next step

[Time buckets](./time-buckets.md) cuts a period into buckets and counts each one.
