# API 레퍼런스

애플리케이션에서 사용하는 공개 타입과 메서드의 목록입니다. 사용 방법은 각 항목의 안내 페이지에 있습니다. 조회 실행기 같은 내부 클래스는 확장 계약이 아니므로 버전 사이에 바뀔 수 있습니다.

## 어노테이션

패키지: `dev.simplecore.searchable.core.annotation`. 안내: [검색 필드 선언](/ko/search-fields.md).

| `@SearchableField` 속성 | 타입 | 기본값 | 의미 |
| --- | --- | --- | --- |
| `entityField` | `String` | `""` | 검색할 엔티티 경로. 비어 있으면 DTO 필드명 |
| `operators` | `SearchOperator[]` | `{}` | 허용하는 연산자. 비어 있으면 모든 연산자 |
| `sortable` | `boolean` | `false` | 정렬 허용 여부 |
| `sortField` | `String` | `""` | 정렬 경로. 비어 있으면 `entityField`, 그다음 DTO 필드명 |
| `keyword` | `boolean` | `false` | `keyword` 파라미터의 검색 대상 여부 |
| `caseSensitivity` | `CaseSensitivity` | `DEFAULT` | `DEFAULT`, `INSENSITIVE`, `SENSITIVE` |
| `match` | `CollectionMatch` | `JOINED` | `JOINED`, `ANY`, `NONE` |
| `aggregatable` | `boolean` | `false` | DTO 필드명으로 요청하는 집계 허용 여부 |

`@SearchableParams`는 컨트롤러 파라미터에 선언합니다. `value()`는 검색 DTO 클래스이며 `SearchCondition<D>` 파라미터에서는 생략할 수 있습니다. `dev.simplecore.searchable.openapi.annotation`의 같은 이름 어노테이션은 deprecated입니다.

## SearchCondition

패키지: `dev.simplecore.searchable.core.condition`. 안내: [요청 작성](/ko/request-formats.md).

| API | 동작 |
| --- | --- |
| `new SearchCondition<>()` | 조건이 없는 검색. 페이지는 지정하지 않은 상태 |
| `getNodes()` | 순서가 있는 조건·그룹 목록. JSON 속성명은 `conditions` |
| `getPage()` / `setPage(Integer)` | 0부터 시작하는 페이지 번호 |
| `getSize()` / `setSize(Integer)` | 페이지 크기 |
| `getSort()` / `setSort(Sort)` | 정렬 |
| `getFetchFields()` / `setFetchFields(Set<String>)` | 서버가 지정하는 fetch 경로. JSON에서 읽지 않음 |
| `fromJson(String, Class<D>)` | JSON 문자열을 변환하고 DTO로 검증 |
| `toJson()` | JSON 문자열로 변환 |

`fromJson`과 `toJson`은 Jackson 2의 `JsonProcessingException`을 선언합니다. 서버 코드가 조건을 직접 구성할 때는 다음 중첩 타입을 사용합니다. 이렇게 만든 조건은 DTO 검증을 거치지 않으므로 빌더를 권장합니다.

| 중첩 타입 | 생성 | 설명 |
| --- | --- | --- |
| `Node` | 없음 | 조건과 그룹의 공통 타입. `getOperator()`, `setOperator(LogicalOperator)` |
| `Condition` | `(LogicalOperator, String field, SearchOperator, Object value, Object value2, String entityField)` | 조건 |
| `Group` | `()`, `(LogicalOperator, List<Node>)` | 순서가 있는 하위 노드 목록 |
| `Sort` | `()` | `addOrder(Order)`로 정렬 추가 |
| `Order` | `(String field, Direction)`, `(String field, Direction, String entityField)` | 정렬 기준 |
| `Direction` | `ASC`, `DESC` | JSON 값은 `asc`, `desc` |

`LogicalOperator`는 `AND`, `OR`이며 JSON 값은 `and`, `or`입니다. `new SearchCondition<>(nodes, page, size)`는 노드 목록과 페이지를 함께 지정합니다.

## 빌더

안내: [요청 작성](/ko/request-formats.md), [연산자와 값](/ko/search-operators.md).

| `SearchConditionBuilder` | 용도 |
| --- | --- |
| `create(DTO.class)` | DTO로 검증하는 빌더 시작 |
| `from(existing, DTO.class)` | 기존 조건을 복사해 확장 |
| `where(...)` | 첫 조건 그룹 |
| `and(...)` / `or(...)` | 앞의 전체 식에 그룹 결합 |
| `sort(...)` | `SortBuilder`의 `asc(field)`, `desc(field)`로 정렬 지정 |
| `page(int)` / `size(int)` | 페이지 지정 |
| `fetchFields(String...)` / `fetchFields(Set<String>)` | fetch 경로 추가 |
| `build()` | 구조와 DTO 선언을 검증하고 조건 반환 |

그룹 안에서 사용하는 조건 메서드:

| 결합 | 메서드 |
| --- | --- |
| 첫 조건, AND | `where`, `equals`, `notEquals`, `greaterThan`, `greaterThanOrEqualTo`, `lessThan`, `lessThanOrEqualTo`, `contains`, `notContains`, `startsWith`, `notStartsWith`, `endsWith`, `notEndsWith`, `isNull`, `isNotNull`, `in`, `notIn`, `between`, `notBetween` |
| OR | `orEquals`, `orNotEquals`, `orGreaterThan`, `orGreaterThanOrEqualTo`, `orLessThan`, `orLessThanOrEqualTo`, `orContains`, `orNotContains`, `orStartsWith`, `orNotStartsWith`, `orEndsWith`, `orNotEndsWith`, `orIsNull`, `orIsNotNull`, `orIn`, `orNotIn`, `orBetween`, `orNotBetween` |
| 중첩 그룹 | `and(...)`, `or(...)` |

범위 메서드는 경계값 두 개, 목록 메서드는 `List`, null 검사는 필드명만 받습니다. `where`, `and`, `or`의 람다는 `ConditionGroupBuilder`를 받습니다. 조건 메서드는 `FirstCondition`과 `ChainedCondition` 인터페이스에 선언되어 있고, 정렬·페이지·fetch 메서드는 `ChainedSearchCondition<D>`를 반환하므로 이어서 호출할 수 있습니다.

## 파라미터 파서

`new SearchableParamsParser<>(DTO.class).convert(Map<String, String>)`는 쿼리 파라미터를 `SearchCondition`으로 변환합니다. 두 번째 생성자 인자에 `true`를 전달하면 검색 파라미터가 아닌 이름을 무시합니다. 예약 파라미터 이름은 `sort`, `page`, `size`, `keyword`이며 `keyword`는 `SearchableParamsParser.KEYWORD_PARAM` 상수로 참조할 수 있습니다.

## 서비스

패키지: `dev.simplecore.searchable.core.service`. 안내: [조회 방법](/ko/reading-results.md), [일괄 수정·삭제](/ko/bulk-write.md).

| 메서드 | 반환 | 의미 |
| --- | --- | --- |
| `findAllWithSearch(condition)` | `Page<T>` | 한 페이지와 전체 건수 |
| `findAllWithSearch(condition, type)` | `Page<P>` | 엔티티 기반 인터페이스 프로젝션 |
| `findOneWithSearch(condition)` | `Optional<T>` | 유일한 한 건. 두 건 이상이면 예외 |
| `findFirstWithSearch(condition)` | `Optional<T>` | 정렬 기준의 첫 한 건 |
| `countWithSearch(condition)` | `long` | 건수 |
| `existsWithSearch(condition)` | `boolean` | 존재 여부 |
| `scrollWithSearch(condition, position)` | `Window<T>` | `Window` 하나 |
| `scrollPosition(cursor)` | `ScrollPosition` | 커서 문자열을 위치로 변환. null이나 빈 값은 첫 위치 |
| `projectWithSearch(condition, type)` | `Page<P>` | 컬럼 프로젝션 |
| `updateWithSearch(condition, patch)` | `long` | 조건에 맞는 모든 행 수정 |
| `deleteWithSearch(condition)` | `long` | 조건에 맞는 모든 행 삭제 |

`scrollPosition`을 제외한 모든 메서드에는 조건 다음에 `Specification<T>` 범위를 받는 오버로드가 있습니다. `SearchableService<T>` 인터페이스는 범위가 없는 조회, 건수, 수정, 삭제 메서드만 선언합니다. 범위 오버로드, 스크롤, 컬럼 프로젝션은 `SearchableServiceSupport`와 `SearchableServiceDelegate`에 있습니다.

| 타입 | 생성 |
| --- | --- |
| `DefaultSearchableService<T, ID>` | `(JpaRepository, EntityManager)`, 선택 인자 `PlatformTransactionManager` |
| `SearchableServiceDelegate<T, ID>` | `(JpaRepository, EntityManager, Class<T>)`, 선택 인자 `PlatformTransactionManager` |
| `SearchableServiceSupport<T, ID>` | `getSearchableDelegate()`를 구현하면 위 메서드를 기본 메서드로 제공 |

`SearchCursor.encode(ScrollPosition)`은 스크롤 위치를 URL에 넣을 수 있는 문자열로 바꾸고, `SearchCursor.decode(String, EntityManager, Class<?>)`는 되돌립니다.

## 집계

타입: `SearchAggregator`. 패키지: `dev.simplecore.searchable.core.service.aggregate`. 안내: [집계](/ko/aggregates.md).

| API | 반환 | 의미 |
| --- | --- | --- |
| `countByValue(entityType, condition, scope, field)` | `List<ValueCount>` | 값별 건수 |
| `aggregate(entityType, condition, scope, aggregates)` | `List<AggregateValue>` | `Aggregate(field, op)`마다 수치 하나 |
| `countByValue`, `aggregate`에 DTO 클래스를 필드 앞에 전달 | 같음 | DTO 필드명 사용, `aggregatable` 필드만 허용 |

`Op`는 `SUM`, `AVG`, `COUNT`, `COUNT_DISTINCT`, `MIN`, `MAX`이고 문자열 이름은 `sum`, `avg`, `count`, `countDistinct`, `min`, `max`입니다. `ValueCount`는 `value`와 `count`, `AggregateValue`는 `field`, `op`, `value`, `result`를 제공합니다.

## 시간 구간 집계

타입: `TimeBucketCounter`, `TimeWindowParams`, `EpochMillisFunctionContributor`. 패키지: `dev.simplecore.searchable.core.service.bucket`. 안내: [시간 구간 집계](/ko/time-buckets.md).

| API | 반환 | 의미 |
| --- | --- | --- |
| `count(entityType, condition, scope, field, from, to, buckets)` | `List<Long>` | 같은 폭의 구간별 건수 |
| `countByCalendar(entityType, condition, scope, field, from, to, unit, zone)` | `List<TimeBucket>` | 달력 단위 구간별 건수 |
| `TimeWindowParams.strip(params)` | `Map<String, String>` | 기간 파라미터 `from`, `to`, `buckets`를 제거한 검색 파라미터 |

`TimeBucket`은 `from`, `to`, `count`를 제공합니다. `count`에는 Repository를 받는 이전 형태의 오버로드도 남아 있습니다. `EpochMillisFunctionContributor`는 `epoch_millis` 함수를 Hibernate에 등록하는 타입이며 애플리케이션에서 호출하지 않습니다.

## 예외

패키지: `dev.simplecore.searchable.core.exception`. 안내: [오류와 메시지](/ko/errors-and-messages.md).

`SearchableException`(기반 타입), `SearchableValidationException`, `SearchableParseException`, `SearchableConfigurationException`, `SearchableJoinException`, `SearchableOperationException`.

## 구성 지원 타입

안내: [확장과 통합](/ko/advanced-features.md).

| 타입 | 기능 |
| --- | --- |
| `SearchRequestLimits` | `setStructureLimits(Structure)`로 요청 구조 제한 지정 |
| `SearchableTimeZoneHolder` | `setZoneId(ZoneId)`로 시간대 지정 |
| `SearchablePatternMatchingHolder` | `setCaseInsensitive(boolean)`으로 패턴의 대소문자 처리 지정 |
| `SearchableConversionHolder` | `setConversionService(ConversionService)`로 값 변환 연결 |
| `MessageUtils` | `init(MessageSource)`로 메시지 연결, `getMessage`로 조회 |
| `SearchableJpaSettings` | 영속성 단위 속성의 키 상수 |
| `SearchableFieldUtils` | DTO 필드와 엔티티 키의 메타데이터 조회, `clearCache()` |
| `SearchableValueParser` | `parseValue(String, Class<?>)`, `parseValueForBetween(String, Class<?>, boolean)`로 문자열을 필드 타입으로 변환 |
| `SearchConditionValidator`, `SearchableFieldValidator` | 빌더와 파서가 사용하는 검증기 |

## starter와 OpenAPI 모듈

안내: [설정](/ko/auto-configuration.md), [OpenAPI](/ko/openapi-integration.md).

| 타입 | 역할 |
| --- | --- |
| `SearchableProperties` | `searchable.*` 설정을 읽는 속성 클래스 |
| `SearchableJpaConfiguration` | 시간대, 요청 제한, 메시지와 변환 서비스 연결, 집계 빈을 구성하는 자동 설정 |
| `SearchableHibernatePropertiesCustomizer` | 영속성 단위 설정과 Hibernate 기본값을 `EntityManagerFactory`에 전달 |
| `SearchableWebConfiguration` | 컨트롤러 파라미터 변환기와 400 응답 처리기를 등록하는 자동 설정 |
| `SearchConditionArgumentResolver` | `@SearchableParams SearchCondition<D>` 파라미터 변환 |
| `SearchableExceptionHandler` | 잘못된 검색에 400으로 응답하는 처리기 |
| `SearchableOpenApiConfiguration` | `searchConditionCustomizer` 빈을 등록하는 자동 설정 |
| `OpenApiDocCustomiser` | 검색 파라미터를 API 문서에 기재하는 `OperationCustomizer` |
