# Declaring search fields

After this page you can declare, per field, the operators it allows, whether it sorts, whether a keyword search reads it, how case is treated and which entity path it searches. The page adds fields to the `PostSearchDTO` of [First search API](/en/basic-usage.md).

## Attributes

`@SearchableField` is declared on a field and every attribute is optional.

| Attribute | Default | When declared | When left out |
| --- | --- | --- | --- |
| `operators` | `{}` | Only the listed operators are allowed | Every operator is allowed |
| `sortable` | `false` | The field can be used in `sort` | A sort by the field is refused |
| `entityField` | `""` | The entity path that is searched | The DTO field name is the entity attribute |
| `sortField` | `""` | The path that is sorted by | `entityField`, then the DTO field name |
| `keyword` | `false` | The reserved `keyword` parameter searches the field | The field is not part of the keyword search |
| `caseSensitivity` | `DEFAULT` | How case is treated for the field | The application setting decides |
| `match` | `JOINED` | How a path through a collection is matched | One element has to satisfy the conditions together |
| `aggregatable` | `false` | An aggregate addressed by the DTO field is allowed | The aggregate is refused |

State `operators` on a public endpoint. Left empty it allows every operator, and only operators that do not fit the value or the field type are refused when the search runs. A field without `@SearchableField` cannot be searched.

## Entity path and sort path

`entityField` is set when the DTO field and the entity attribute have different names, or when an attribute of a related entity is searched. A client uses DTO field names only and cannot name an entity path.

<!-- snippet: PostSearchDTO.java#related -->
```java
@SearchableField(entityField = "author.name", operators = {EQUALS, CONTAINS}, sortable = true,
        sortField = "author.familyName", keyword = true, caseSensitivity = CaseSensitivity.INSENSITIVE)
private String authorName;
```

`authorName` searches the name of the author (`author.name`) and sorts by the family name (`author.familyName`).

<!-- snippet: related-sort.http -->
```http
GET /api/posts?sort=authorName.asc,id.asc
```

With the example data the two posts by Kim come first, then the post by Lee, then the post by Park. The primary key is added after the orders the client sent, so rows with equal sort values keep one order.

## Keyword search

A field with `keyword = true` is searched by the reserved parameter `keyword`. A row matches when any keyword field contains the text, and that result is combined with the other conditions by AND.

<!-- snippet: keyword.http -->
```http
GET /api/posts?keyword=Spring&status.equals=PUBLISHED
```

In the example DTO `title` and `authorName` are keyword fields. The request returns Spring Boot basics, whose title or author name contains `Spring` and whose status is `PUBLISHED`. A keyword field must be a `String` whose `operators` allow `CONTAINS`. A DTO without a keyword field refuses the parameter.

<!-- snippet: keyword-unsupported.http -->
```http
GET /api/posts/simple?keyword=Spring
```

## Case handling

The pattern operators (`CONTAINS`, `STARTS_WITH`, `ENDS_WITH` and their negations) compare as the database does by default. `caseSensitivity` decides for one field.

| Value | Behavior |
| --- | --- |
| `DEFAULT` | Pattern operators follow `searchable.pattern-matching.case-insensitive`; the other operators compare as the database does |
| `INSENSITIVE` | Pattern operators and `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN` ignore case |
| `SENSITIVE` | The field compares as the database does even when the application setting ignores case |

<!-- snippet: related-author.http -->
```http
GET /api/posts?authorName.contains=kim&sort=id.asc
```

`authorName` is `INSENSITIVE`, so lower-case `kim` finds the two posts by Minjun Kim. Ignoring case compares the lower-cased column with the lower-cased value, so an index that serves such a search is built on the same expression (`lower(column)`). On PostgreSQL with the `pg_trgm` extension, for example: `create index on plans using gin (lower(name) gin_trgm_ops)`.

## Fields without a value

Allow `IS_NULL` or `IS_NOT_NULL` to search for the presence of a value.

<!-- snippet: PostSearchDTO.java#nullable -->
```java
@SearchableField(operators = {IS_NULL, IS_NOT_NULL, BETWEEN})
private LocalDateTime publishedAt;
```

<!-- snippet: op-null.http -->
```http
GET /api/posts?publishedAt.isNull=true
```

The request returns Query tuning draft, which has no publication time.

## Constraints on search values

Bean Validation constraints declared on a DTO field (`@Size`, `@Min`, `@Pattern` and others) apply to every value searched on that field. A range operator checks both bounds. Constraints of fields that are not searched are not checked, so a `@NotNull` field does not prevent a search on other fields.

<!-- snippet: PostSearchDTO.java#described -->
```java
@SearchableField(operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true, keyword = true)
@Schema(description = "Post title", example = "Spring")
@Size(min = 2, max = 100)
private String title;
```

<!-- snippet: constraint.http -->
```http
GET /api/posts?title.contains=a
```

A search value of `title` needs at least two characters, so this request is refused. The constraint applies to the text of a pattern operator too, which makes a minimum length a way to refuse one-character searches over a whole table. The message of a violated constraint is the text of Bean Validation, in the locale of the request.

## DTO shapes

Only the field declarations of a search DTO are read, so each of these shapes works.

| Shape | Note |
| --- | --- |
| A class with fields only | No accessors are needed |
| A record | `@SearchableField` is declared on the components |
| Inheritance | The fields of the superclass are read as well |

<!-- snippet: PostQuery.java#record -->
```java
public record PostQuery(
        @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true) String title,
        @SearchableField(operators = {EQUALS}) PostStatus status) {
}
```

<!-- snippet: TimestampSearchDTO.java#base -->
```java
public class TimestampSearchDTO {

    @SearchableField(operators = {BETWEEN}, sortable = true)
    private Instant createdAt;
}
```

<!-- snippet: ArchivedPostSearchDTO.java#inherited -->
```java
public class ArchivedPostSearchDTO extends TimestampSearchDTO {

    @SearchableField(operators = {CONTAINS})
    private String title;
}
```

`ArchivedPostSearchDTO` searches both `title` and the `createdAt` of its superclass.

## Declaration and access control

A DTO is the allow-list of fields, operators and sorts a client may use. Define separate DTOs for endpoints that expose different things. A condition the server decides, such as a tenant or an owner, is not taken as a DTO field; combine it as [Server scope and limits](/en/server-scope-and-limits.md) shows.

## Next step

[Writing requests](/en/request-formats.md) searches the declared fields with GET parameters, a JSON body and the Java builder.
