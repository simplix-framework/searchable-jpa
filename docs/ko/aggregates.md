# 집계

이 페이지를 마치면 목록과 같은 검색 조건으로 값별 건수, 합계, 평균, 최소·최대를 데이터베이스에서 계산할 수 있습니다. 조건과 범위는 [서버 범위와 제한](./server-scope-and-limits.md)까지의 내용을 사용합니다.

## SearchAggregator

starter는 `SearchAggregator` 빈을 등록하므로 주입해서 사용합니다. starter 없이 쓸 때는 `new SearchAggregator(entityManager)`로 생성합니다. 모든 메서드는 엔티티 타입, 검색 조건, 서버 범위(없으면 `null`)를 받습니다.

## 값별 건수

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

- 값마다 조건에 맞는 루트 엔티티 수를 셉니다. 컬렉션 조건에 맞는 요소가 여러 개여도 한 건입니다.
- enum 필드는 건수가 0인 상수까지 선언 순서로 모두 반환합니다. 다른 타입은 존재하는 값을 건수가 많은 순서로 반환합니다.
- 반환하는 값은 최대 `searchable.aggregate.max-groups`개(기본 1,000)입니다.
- 결과는 `ValueCount(value, count)`의 목록입니다.

## 합계와 평균

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

여러 수치를 한 번의 조회로 계산합니다. 요청은 `Aggregate(field, op)`의 목록이고 결과는 같은 순서의 `AggregateValue(field, op, value, result)` 목록입니다. `value`는 숫자 결과이고, `result`는 날짜처럼 숫자가 아닌 결과까지 담습니다.

| 연산 | 이름 | 적용 대상 |
| --- | --- | --- |
| `Op.SUM` | `sum` | 숫자 |
| `Op.AVG` | `avg` | 숫자 |
| `Op.COUNT` | `count` | 모든 타입 |
| `Op.COUNT_DISTINCT` | `countDistinct` | 모든 타입 |
| `Op.MIN` | `min` | 숫자, 날짜 등 순서를 비교할 수 있는 타입 |
| `Op.MAX` | `max` | 숫자, 날짜 등 순서를 비교할 수 있는 타입 |

이름은 `Op.parse`가 읽는 문자열이며 클라이언트가 연산을 지정하는 API에서 사용합니다.

## 필드를 지정하는 주체

| 호출 형태 | 필드 이름 | 허용 조건 |
| --- | --- | --- |
| DTO 클래스를 함께 전달 | DTO 필드명 | `@SearchableField(aggregatable = true)`인 필드만 |
| DTO 클래스 없이 호출 | 엔티티 속성명 | 서버 코드가 고른 필드 |

위의 예제는 모두 DTO 클래스(`PostSearchDTO.class`)를 전달하므로 `aggregatable`로 선언한 `status`와 `viewCount`만 집계할 수 있습니다. 클라이언트가 필드 이름을 정하는 API에는 이 형태를 사용하세요. 값별 건수는 필드의 값 자체를 반환하므로, 값을 공개해도 되는 필드만 `aggregatable`로 선언하세요.

서버 코드가 필드를 고를 때는 엔티티 속성명을 직접 사용합니다.

<!-- snippet: PostStatsController.java#server-figures -->
```java
public List<AggregateValue> newestAndTotal(SearchCondition<PostSearchDTO> condition) {
    return aggregator.aggregate(Post.class, condition, null,
            List.of(new Aggregate("createdAt", Op.MAX), new Aggregate("id", Op.COUNT)));
}
```

가장 최근 생성 시각(`result`가 `Instant`)과 전체 건수를 반환합니다.

## 거부되는 요청

| 요청 | 결과 |
| --- | --- |
| `aggregatable`이 아닌 DTO 필드 | `SearchableValidationException` |
| 엔티티에 없는 필드, 컬렉션 필드 | `SearchableValidationException` |
| 타입에 맞지 않는 연산 (enum의 `sum` 등) | `SearchableValidationException` |
| 알 수 없는 연산 이름 | `SearchableValidationException` |

## 관련 설정

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `searchable.aggregate.max-groups` | 1000 | 값별 건수가 반환하는 값의 최대 개수 |

## 다음 단계

[시간 구간 집계](./time-buckets.md)에서 기간을 나누어 구간별 건수를 계산합니다.
