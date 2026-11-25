# 업그레이드 안내

버전을 올릴 때 확인할 동작 변경과 제거한 API입니다. 버전별 전체 변경 내용은 [변경 사항](/ko/changelog.md)에 있습니다.

## 2.0.x에서 2.1.0으로

### 달라지는 동작

| 영역 | 2.0.x | 2.1.0 |
| --- | --- | --- |
| starter 설정 | 페이지 크기와 Hibernate 기본값이 `EntityManagerFactory`에 전달되지 않았습니다 | 전달되어 적용됩니다. 설정한 값이 실제로 동작하는지 확인하세요 |
| 시간대 ID | 잘못된 ID는 무시하고 다음 순위의 설정을 사용했습니다 | 애플리케이션 시작을 중단합니다 |
| 달력에 없는 날짜 | 가까운 날짜로 바꿔 검색했습니다 | 거부합니다 |
| 날짜만 쓴 범위의 끝 | JSON과 빌더에서는 끝 날짜의 0시까지만 포함했습니다 | 모든 입력 형식에서 끝 날짜 전체를 포함합니다 |
| GET의 문자열 값 | 앞뒤 공백을 지우고 정규화했으며 `null` 문자열을 값 없음으로 읽었습니다 | 보낸 문자열 그대로 검색합니다 |
| `size`만 보낸 요청 | 거부했습니다 | 0번 페이지를 조회합니다 |
| 컬렉션 조건의 SQL | 조인과 `SELECT DISTINCT`를 사용했습니다 | `EXISTS`를 사용합니다. 결과 행과 건수는 같습니다 |
| 연관이 아닌 fetch 경로 | 무시했습니다 | `SearchableJoinException`이 발생합니다 |
| `countWithSearch`, `existsWithSearch` | 조건의 페이지와 크기를 검증했습니다 | 검증하지 않습니다 |
| 집계와 시간 구간의 입력 오류 | `IllegalArgumentException`이나 JPA 구현체의 예외가 발생했습니다 | `SearchableValidationException`이 발생합니다 |
| 값별 건수 | 모든 값을 반환했습니다 | 건수가 많은 순서로 `searchable.aggregate.max-groups`개까지 반환합니다 |
| `@IdClass` 키의 정렬 순서 | 메타모델이 반환하는 순서였습니다 | 식별자 클래스에 선언한 순서입니다 |
| DTO 제약 위반 메시지의 언어 | JVM의 기본 로캘이었습니다 | 요청의 로캘을 따릅니다 |
| 한국어 로캘의 메시지 | 한도·서비스·웹 메시지가 영어였습니다 | 번들의 한국어 문구를 사용합니다 |
| OpenAPI의 enum 파라미터 | 스키마 참조로 기재했습니다 | 파라미터에 값 목록을 직접 기재합니다 |
| OpenAPI의 `sort` | 반복 파라미터 배열로 기재했습니다 | 쉼표로 구분한 값 하나로 기재합니다 |
| `searchable-jpa-core`의 전이 의존성 | SpringDoc과 Spring MVC를 포함했습니다 | 포함하지 않습니다. starter는 계속 포함합니다 |

### 제거한 API

| 제거한 항목 | 대신 사용할 것 |
| --- | --- |
| `JoinStrategyManager` | 없음. 조인은 조건과 정렬에서 자동으로 구성합니다 |
| `SearchableFieldUtils.extractJoinPaths` | 없음 |
| `RelationshipAnalyzer`의 `isValidPath`, `countToManyPaths`, `detectManyToManyFields` | 없음. 내부 분석 메서드였습니다 |
| `OpenApiDocUtils.toCamelCase` | 없음 |
| `SearchableJpaConfiguration`의 `configureHibernateOptimizations`, `configureSearchLimits`와 기존 생성자 | starter의 자동 설정을 그대로 사용하세요 |

### deprecated

| 항목 | 설명 |
| --- | --- |
| `searchable.hibernate.batch-versioned-data` | Hibernate 7이 읽지 않는 설정입니다. 설정 파일에서 삭제하세요 |
| `dev.simplecore.searchable.openapi.annotation.SearchableParams` | `dev.simplecore.searchable.core.annotation.SearchableParams`로 바꾸세요. 기존 어노테이션도 계속 동작합니다 |

### 새로 사용할 수 있는 기능

[서버 범위](/ko/server-scope-and-limits.md), [스크롤과 컬럼 프로젝션](/ko/reading-results.md), [컬렉션 비교 방식](/ko/relationship-and-two-phase-query.md), [키워드 검색과 필드별 대소문자 처리](/ko/search-fields.md), [컨트롤러 파라미터 변환](/ko/request-formats.md), [400 응답과 메시지 재정의](/ko/errors-and-messages.md), [집계](/ko/aggregates.md), [달력 구간](/ko/time-buckets.md)을 추가했습니다.

## 1.x에서 2.x로

2.x는 Spring Boot 4 계열 전용입니다. 애플리케이션을 Boot 4로 이전한 뒤 라이브러리를 2.x로 올리세요. 서비스 상속 구조, 델리게이트 생성 방식, 쿼리 파라미터 이름, Jackson 2 메서드 시그니처는 유지합니다. 문서에 명시한 한도를 넘는 요청은 2.x에서 거부합니다.

## 0.1.x

0.1.x는 Spring Boot 2.7과 `javax.*` 패키지를 사용하며 지원을 종료했습니다.
