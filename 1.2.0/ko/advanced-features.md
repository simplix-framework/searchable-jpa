# 확장과 통합

이 페이지를 마치면 다른 클래스를 상속한 서비스에 검색 기능을 조합하고, 트랜잭션 관리자를 지정하며, starter 없이 core 모듈만으로 구성할 수 있습니다.

## 서비스 조합

`DefaultSearchableService`를 상속할 수 없는 서비스는 `SearchableServiceSupport`를 구현하고 `SearchableServiceDelegate`를 반환합니다. 조회, 스크롤, 프로젝션, 수정, 삭제 메서드는 인터페이스의 기본 메서드로 제공합니다.

<!-- snippet: PostArchiveService.java#composition -->
```java
@Service
public class PostArchiveService implements SearchableServiceSupport<Post, Long> {

    private final SearchableServiceDelegate<Post, Long> delegate;

    public PostArchiveService(PostRepository repository, EntityManager entityManager,
                              PlatformTransactionManager transactionManager) {
        this.delegate = new SearchableServiceDelegate<>(repository, entityManager, Post.class, transactionManager);
    }

    @Override
    public SearchableServiceDelegate<Post, Long> getSearchableDelegate() {
        return delegate;
    }
}
```

- Repository는 `JpaRepository`와 `JpaSpecificationExecutor`를 모두 구현해야 합니다.
- `EntityManager`는 Repository와 같은 영속성 단위의 Spring 관리 인스턴스를 사용하세요.
- `DefaultSearchableService`도 내부에서 같은 델리게이트를 사용하므로 어느 방식이든 동작은 같습니다.

## 트랜잭션

조회는 진행 중인 트랜잭션이 있으면 참여하고, 없으면 읽기 전용 트랜잭션을 시작합니다. 수정과 삭제는 진행 중인 트랜잭션에 참여하거나 작업 전체를 한 트랜잭션으로 실행합니다.

| 생성 방식 | 트랜잭션 처리 |
| --- | --- |
| `PlatformTransactionManager`를 생성자에 전달 | 모든 작업을 그 관리자로 실행합니다 |
| 전달하지 않음 | JPA 트랜잭션 밖에서 호출하면 자체 JPA 트랜잭션을 시작합니다 |

JTA나 여러 데이터소스를 묶는 트랜잭션 관리자를 사용하는 애플리케이션은 위 예제처럼 관리자를 전달하세요. `DefaultSearchableService`도 같은 인자를 받는 생성자가 있습니다. 라이브러리는 격리 수준을 바꾸지 않으며, 읽기 전용 트랜잭션이 반복 읽기를 보장하지는 않습니다.

## starter 없이 사용

`searchable-jpa-core`만 추가하면 자동 설정이 없으므로 다음 항목을 애플리케이션이 연결합니다.

| 항목 | starter가 하는 일 | 직접 연결하는 방법 |
| --- | --- | --- |
| 시간대 | 설정에서 정해 지정 | `SearchableTimeZoneHolder.setZoneId` |
| 패턴의 대소문자 처리 | 설정 값 지정 | `SearchablePatternMatchingHolder.setCaseInsensitive` |
| 값 변환 | Spring MVC의 변환 서비스 연결 | `SearchableConversionHolder.setConversionService` |
| 요청 구조 제한 | 설정 값 지정 | `SearchRequestLimits.setStructureLimits` |
| 메시지 | 애플리케이션의 `MessageSource` 연결 | `MessageUtils.init` |
| 영속성 단위 설정 | `EntityManagerFactory` 속성으로 전달 | `SearchableJpaSettings`의 키를 속성에 지정 |

<!-- snippet: CoreOnlySetup.java#process -->
```java
public static void applyProcessSettings(ZoneId zone, ConversionService conversionService,
                                        MessageSource messageSource) {
    SearchableTimeZoneHolder.setZoneId(zone);
    SearchablePatternMatchingHolder.setCaseInsensitive(false);
    SearchableConversionHolder.setConversionService(conversionService);
    SearchRequestLimits.setStructureLimits(new SearchRequestLimits.Structure(1000, 32, 1000, 16));
    MessageUtils.init(messageSource);
}
```

이 값들은 프로세스 전체에 적용하므로 애플리케이션 시작 시 한 번 지정하세요.

<!-- snippet: CoreOnlySetup.java#unit -->
```java
public static Map<String, Object> persistenceUnitSettings() {
    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put(SearchableJpaSettings.DEFAULT_PAGE_SIZE, 20);
    settings.put(SearchableJpaSettings.MAX_PAGE_SIZE, 1000);
    settings.put(SearchableJpaSettings.AUTO_FETCH_COLLECTIONS, true);
    settings.put(SearchableJpaSettings.IN_CLAUSE_BATCH_SIZE, 500);
    settings.put(SearchableJpaSettings.WRITE_CHUNK_SIZE, 200);
    settings.put(SearchableJpaSettings.REQUIRE_WRITE_FILTER, false);
    settings.put(SearchableJpaSettings.MAX_AGGREGATE_GROUPS, 1000);
    settings.put(SearchableJpaSettings.MAX_BUCKETS, 512);
    return settings;
}
```

위 맵을 `EntityManagerFactory`를 만들 때 JPA 속성에 추가합니다. 지정하지 않은 키는 기본값을 사용합니다.

- 컨트롤러에서는 `SearchableParamsParser`를 직접 호출합니다. [요청 작성](/ko/request-formats.md)의 `Map` 방식입니다.
- `TimeBucketCounter`와 `SearchAggregator`는 `EntityManager`로 직접 생성합니다.
- 잘못된 요청의 응답은 애플리케이션의 예외 처리기에서 구성합니다.
- 시간 구간 집계의 `epoch_millis` 함수는 Hibernate의 서비스 로더로 등록되므로 starter 없이도 적용됩니다.

## 여러 영속성 단위

페이지 크기, IN 배치 크기, 쓰기 처리 단위, 집계와 구간 상한은 영속성 단위마다 읽습니다. starter는 Spring Boot가 만드는 기본 단위에만 값을 전달하므로, 직접 구성한 단위에는 [설정](/ko/auto-configuration.md)의 영속성 단위 속성을 지정하세요. 시간대, 요청 구조 제한, 패턴의 대소문자 처리는 프로세스 전체에 하나만 적용합니다.

## Jackson 2와 Jackson 3

`SearchCondition`의 JSON 도우미(`fromJson`, `toJson`)는 Jackson 2를 사용합니다. 타입 인자가 있는 `SearchCondition`은 Jackson 2와 Jackson 3에서 모두 역직렬화하며 같은 DTO 검증을 거칩니다. Spring Boot 3은 HTTP 본문을 Jackson 2로 변환합니다.

## 다음 단계

타입과 메서드의 전체 목록은 [API 레퍼런스](/ko/api-reference.md)에 있습니다. 동작이 예상과 다르면 [문제 해결](/ko/faq.md)을 확인하세요.
