# 검색 필드 선언

이 페이지를 마치면 필드마다 허용할 연산자, 정렬, 키워드 검색, 대소문자 처리, 엔티티 경로를 선언할 수 있습니다. [첫 검색 API](./basic-usage.md)의 `PostSearchDTO`에 필드를 추가하며 진행합니다.

## 속성

`@SearchableField`는 필드에 선언하며 속성은 모두 선택입니다.

| 속성 | 기본값 | 선언했을 때 | 선언하지 않았을 때 |
| --- | --- | --- | --- |
| `operators` | `{}` | 적은 연산자만 허용합니다 | 모든 연산자를 허용합니다 |
| `sortable` | `false` | `sort`에 필드를 사용할 수 있습니다 | 정렬 요청을 거부합니다 |
| `entityField` | `""` | 적은 엔티티 경로로 검색합니다 | DTO 필드명을 엔티티 속성명으로 사용합니다 |
| `sortField` | `""` | 적은 경로로 정렬합니다 | `entityField`, 그다음 DTO 필드명으로 정렬합니다 |
| `keyword` | `false` | 예약 파라미터 `keyword`의 검색 대상에 포함합니다 | 키워드 검색에서 제외합니다 |
| `caseSensitivity` | `DEFAULT` | 필드의 대소문자 처리를 지정합니다 | 애플리케이션 설정을 따릅니다 |
| `match` | `JOINED` | 컬렉션을 거치는 경로의 비교 방식을 지정합니다 | 같은 요소가 조건을 함께 만족해야 합니다 |
| `aggregatable` | `false` | DTO 필드명으로 요청하는 집계를 허용합니다 | 집계 요청을 거부합니다 |

공개 엔드포인트에서는 `operators`를 명시하세요. 비워 두면 모든 연산자를 허용하며, 값과 필드 타입에 맞지 않는 연산자만 실행 시점에 거부합니다. `@SearchableField`가 없는 필드는 검색에 사용할 수 없습니다.

## 엔티티 경로와 정렬 경로

DTO 필드명과 엔티티 속성명이 다르거나 연관 엔티티의 속성을 검색할 때 `entityField`를 지정합니다. 클라이언트는 DTO 필드명만 사용하며 엔티티 경로를 직접 지정할 수 없습니다.

<!-- snippet: PostSearchDTO.java#related -->
```java
@SearchableField(entityField = "author.name", operators = {EQUALS, CONTAINS}, sortable = true,
        sortField = "author.familyName", keyword = true, caseSensitivity = CaseSensitivity.INSENSITIVE)
private String authorName;
```

`authorName`은 작성자 이름(`author.name`)으로 검색하고 성(`author.familyName`)으로 정렬합니다.

<!-- snippet: related-sort.http -->
```http
GET /api/posts?sort=authorName.asc,id.asc
```

예제 데이터에서는 Kim이 쓴 두 건, Lee가 쓴 한 건, Park이 쓴 한 건의 순서로 반환합니다. 클라이언트가 보낸 순서 뒤에는 기본키 정렬을 추가해, 정렬값이 같은 행의 순서를 고정합니다.

## 키워드 검색

`keyword = true`인 필드는 예약 파라미터 `keyword`의 검색 대상입니다. 키워드 필드 중 하나라도 검색어를 포함하면 일치로 판정하고, 이 판정을 다른 조건과 AND로 결합합니다.

<!-- snippet: keyword.http -->
```http
GET /api/posts?keyword=Spring&status.equals=PUBLISHED
```

예제 DTO에서는 `title`과 `authorName`이 키워드 필드입니다. 위 요청은 제목이나 작성자 이름에 `Spring`이 포함되고 상태가 `PUBLISHED`인 Spring Boot basics를 반환합니다. 키워드 필드는 `String` 타입이어야 하고 `operators`가 `CONTAINS`를 허용해야 합니다. 키워드 필드가 없는 DTO에 `keyword`를 보내면 거부합니다.

<!-- snippet: keyword-unsupported.http -->
```http
GET /api/posts/simple?keyword=Spring
```

## 대소문자 처리

패턴 연산자(`CONTAINS`, `STARTS_WITH`, `ENDS_WITH`와 부정형)는 기본적으로 데이터베이스의 비교 규칙을 따릅니다. `caseSensitivity`로 필드별 처리를 지정합니다.

| 값 | 동작 |
| --- | --- |
| `DEFAULT` | 패턴 연산자는 `searchable.pattern-matching.case-insensitive` 설정을 따르고, 나머지 연산자는 데이터베이스의 비교 규칙을 따릅니다 |
| `INSENSITIVE` | 패턴 연산자와 `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`에서 대소문자를 구분하지 않습니다 |
| `SENSITIVE` | 애플리케이션 설정이 대소문자 무시여도 데이터베이스의 비교 규칙을 따릅니다 |

<!-- snippet: related-author.http -->
```http
GET /api/posts?authorName.contains=kim&sort=id.asc
```

`authorName`은 `INSENSITIVE`이므로 소문자 `kim`으로 Minjun Kim의 게시글 두 건을 찾습니다. 대소문자를 무시할 때는 컬럼과 값을 모두 소문자로 바꿔 비교하므로, 이 검색에 사용할 인덱스는 같은 식(`lower(컬럼)`)으로 만들어야 합니다. PostgreSQL에서는 `pg_trgm` 확장으로 `create index on plans using gin (lower(name) gin_trgm_ops)`처럼 만듭니다.

## 값이 없는 필드

null 여부를 검색하려면 `IS_NULL`이나 `IS_NOT_NULL`을 허용합니다.

<!-- snippet: PostSearchDTO.java#nullable -->
```java
@SearchableField(operators = {IS_NULL, IS_NOT_NULL, BETWEEN})
private LocalDateTime publishedAt;
```

<!-- snippet: op-null.http -->
```http
GET /api/posts?publishedAt.isNull=true
```

게시 시각이 없는 Query tuning draft를 반환합니다.

## 검색값 제약

DTO 필드에 선언한 Bean Validation 제약(`@Size`, `@Min`, `@Pattern` 등)은 그 필드로 검색하는 모든 값에 적용합니다. 범위 연산자는 두 경계값을 모두 검사합니다. 검색에 사용하지 않은 필드의 제약은 검사하지 않으므로 `@NotNull` 필드가 있어도 다른 필드만으로 검색할 수 있습니다.

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

`title`의 검색값은 두 글자 이상이어야 하므로 이 요청은 거부합니다. 패턴 연산자의 검색어에도 같은 제약을 적용하므로, 최소 길이 제약으로 한 글자 검색처럼 대상이 넓은 요청을 거부할 수 있습니다. 제약 위반 메시지는 Bean Validation의 문구이며 요청의 로캘을 따릅니다.

## DTO 형태

검색 DTO는 필드 선언만 읽으므로 다음 형태를 모두 사용할 수 있습니다.

| 형태 | 설명 |
| --- | --- |
| 필드만 있는 클래스 | 접근자가 없어도 됩니다 |
| record | 구성 요소에 `@SearchableField`를 선언합니다 |
| 상속 | 상위 클래스의 필드를 함께 읽습니다 |

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

`ArchivedPostSearchDTO`로는 `title`과 상위 클래스의 `createdAt`을 모두 검색할 수 있습니다.

## 선언과 접근 통제

DTO는 클라이언트가 사용할 수 있는 필드, 연산자, 정렬의 허용 목록입니다. 엔드포인트마다 공개 범위가 다르면 DTO를 따로 정의하세요. 테넌트나 소유자처럼 서버가 정하는 조건은 DTO 필드로 받지 말고 [서버 범위와 제한](./server-scope-and-limits.md)의 방식으로 결합하세요.

## 다음 단계

[요청 작성](./request-formats.md)에서 선언한 필드를 GET 파라미터, JSON 본문, Java 빌더로 검색합니다.
