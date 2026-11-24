# 문제 해결

증상별로 확인할 항목입니다. 각 항목의 배경은 연결한 안내 페이지에 있습니다.

## 의존성과 시작 오류

| 증상 | 확인할 항목 |
| --- | --- |
| 클래스를 찾지 못하거나 시작 중 오류 발생 | Boot 4에는 2.x, Boot 3에는 1.x를 사용하고 모든 모듈의 버전을 맞추세요. `./gradlew dependencyInsight --dependency hibernate-core --configuration runtimeClasspath`로 버전이 바뀌지 않았는지 확인하세요 |
| GitHub Packages 인증 실패 | 저장소 URL, 사용자명, 토큰의 패키지 읽기 권한을 확인하세요 |
| 로컬 설치한 버전을 찾지 못함 | `./gradlew publishToMavenLocal`로 모든 모듈을 설치하고 `mavenLocal()`을 추가했는지 확인하세요 |
| 자동 설정이 적용되지 않음 | starter가 런타임 클래스패스에 있는지, `SearchableJpaConfiguration`을 제외하지 않았는지 확인하세요. `debug=true`로 조건 평가 보고서를 볼 수 있습니다 |
| 서비스 생성 중 오류 | Repository가 `JpaRepository`와 `JpaSpecificationExecutor`를 모두 상속하는지 확인하세요 |
| 방언 클래스 오류로 시작 실패 | 방언을 직접 지정하지 말고 Hibernate가 JDBC 메타데이터에서 판별하게 하세요. 이전 Hibernate 버전의 방언 클래스명은 동작하지 않습니다 |
| 시간대 ID 오류로 시작 중단 | `searchable.date-time.default-timezone`과 `spring.jackson.time-zone`의 값을 확인하세요 |

## 요청이 거부됨

| 증상 | 확인할 항목 |
| --- | --- |
| 필드를 찾을 수 없음 | 파라미터 이름이 DTO 필드명인지, 필드에 `@SearchableField`가 있는지 확인하세요. [검색 필드 선언](./search-fields.md) |
| 연산자를 사용할 수 없음 | `operators`에 연산자가 있는지, 이름이 `greaterThanOrEqualTo`처럼 정확한지 확인하세요. [연산자와 값](./search-operators.md) |
| 정렬이 거부됨 | `sortable = true`인지, 형식이 `sort=createdAt.desc`인지 확인하세요 |
| 날짜 값이 거부됨 | 형식과 달력에 있는 날짜인지 확인하세요. `LocalDateTime` 필드에는 시간까지 보내세요 |
| `between`이 거부됨 | 순서가 맞는 값 두 개가 필요합니다 |
| 알 수 없는 파라미터로 거부됨 | 자체 파라미터를 제거한 뒤 파서를 호출하거나 `searchable.params.ignore-unknown`을 사용하세요. [요청 작성](./request-formats.md) |
| 페이지 크기나 조건 수 초과 | [서버 범위와 제한](./server-scope-and-limits.md)의 한도를 확인하세요 |
| 잘못된 요청에 500으로 응답 | `searchable.web.problem-details.enabled`를 켜거나 예외 처리기를 작성하세요. [오류와 메시지](./errors-and-messages.md) |

## AND와 OR의 결과가 예상과 다름

조건은 선언한 순서대로 왼쪽부터 결합합니다. `A, or B, and C`는 `(A OR B) AND C`입니다. `A OR (B AND C)`가 필요하면 B와 C를 그룹으로 묶으세요. 그룹의 연결 방식은 항목이 하나뿐인 그룹에도 적용합니다. [요청 작성](./request-formats.md)의 그림을 참고하세요.

## 조회가 느리거나 SQL이 많음

| 증상 | 확인할 항목 |
| --- | --- |
| 한 요청에 SQL이 여러 번 실행됨 | 정렬이나 fetch가 컬렉션을 거치면 키 조회와 엔티티 조회로 나누어 실행합니다. 컬렉션마다 조회가 추가됩니다. [조회 전략과 성능](./two-phase-query-optimization.md) |
| 필요 없는 컬렉션을 조회함 | `searchable.hibernate.auto-fetch-collections=false`로 설정하고 필요한 경로만 `fetchFields`로 지정하세요 |
| 뒤쪽 페이지가 느림 | keyset 스크롤을 사용하세요. [조회 방법](./reading-results.md) |
| `contains` 검색이 느림 | 앞부분이 정해지지 않은 패턴은 일반 인덱스를 사용하지 못합니다 |
| 건수 조회가 실행되지 않음 | 조회한 행이 페이지 크기보다 적으면 건수를 계산으로 구합니다 |

실행한 SQL은 `org.hibernate.SQL=DEBUG`, 바인딩 값은 `org.hibernate.orm.jdbc.bind=TRACE`로 확인합니다.

## 복합키, 프로젝션, 수정

| 증상 | 확인할 항목 |
| --- | --- |
| `@EmbeddedId` 필드로 검색되지 않음 | `entityField`를 `id.tenantId`처럼 실제 경로로 선언하세요 |
| keyset 스크롤이 거부됨 | `@EmbeddedId` 엔티티는 오프셋 스크롤만 지원합니다 |
| 프로젝션 타입이 거부됨 | `findAllWithSearch`의 프로젝션은 인터페이스만, `projectWithSearch`는 인터페이스와 record를 받습니다 |
| 프로젝션을 썼는데 모든 컬럼을 조회함 | `findAllWithSearch`의 프로젝션은 엔티티를 조회합니다. `projectWithSearch`를 사용하세요 |
| 수정에서 값이 바뀌지 않음 | 패치의 null 속성, 식별자, 버전, 감사 필드, `updatable=false` 컬럼은 변경하지 않습니다. [일괄 수정·삭제](./bulk-write.md) |
| 수정에서 0이나 false가 덮어써짐 | 패치 객체의 기본형 속성을 참조 타입으로 바꾸세요 |

## 시간 구간과 JSON

| 증상 | 확인할 항목 |
| --- | --- |
| 구간 건수의 합이 목록 건수와 다름 | 구간은 끝을 제외하고, 시각이 null인 행을 세지 않습니다. 기간과 시간축 필드를 확인하세요. [시간 구간 집계](./time-buckets.md) |
| `LocalDateTime` 축의 구간이 어긋남 | 애플리케이션 시간대의 시각으로 해석합니다. [설정](./auto-configuration.md)의 시간대 결정 순서를 확인하세요 |
| JSON 본문이 변환되지 않음 | 컨트롤러 파라미터에 `SearchCondition<MySearchDto>`처럼 타입 인자를 명시하세요 |

## 문서 버전 선택기

버전 선택기는 대상 버전에 같은 페이지가 있으면 언어, 페이지, 제목 위치를 유지합니다. 페이지가 없으면 그 버전의 같은 언어 시작 페이지로, 그것도 없으면 영어 시작 페이지로 이동합니다. 네트워크 오류가 발생하면 현재 페이지를 유지합니다. 버전 디렉터리가 없는 로컬 미리보기에는 선택기를 표시하지 않습니다.
