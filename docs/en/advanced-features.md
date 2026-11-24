# Extension and integration

After this page you can add the search to a service that extends another class, name the transaction manager, and run the core module without the starter.

## Composing a service

A service that cannot extend `DefaultSearchableService` implements `SearchableServiceSupport` and returns a `SearchableServiceDelegate`. The read, scroll, projection, update and delete methods are default methods of the interface.

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

- The repository has to implement both `JpaRepository` and `JpaSpecificationExecutor`.
- Use the Spring-managed `EntityManager` of the same persistence unit as the repository.
- `DefaultSearchableService` uses the same delegate internally, so both ways behave the same.

## Transactions

A read joins a transaction that is in progress, or starts a read-only one. An update or a delete joins a transaction that is in progress, or runs the whole operation in one transaction.

| Construction | Transaction handling |
| --- | --- |
| A `PlatformTransactionManager` is passed to the constructor | Every operation runs through that manager |
| None is passed | A call outside a JPA transaction starts a JPA transaction of its own |

An application with JTA, or with a transaction manager that spans several datasources, passes the manager as the example above does. `DefaultSearchableService` has a constructor with the same argument. The library does not change the isolation level, and a read-only transaction does not guarantee repeatable reads.

## Without the starter

With `searchable-jpa-core` alone there is no auto-configuration, so the application wires these items.

| Item | What the starter does | Wiring it yourself |
| --- | --- | --- |
| Time zone | Decides it from the settings | `SearchableTimeZoneHolder.setZoneId` |
| Case of patterns | Applies the setting | `SearchablePatternMatchingHolder.setCaseInsensitive` |
| Value conversion | Connects the conversion service of Spring MVC | `SearchableConversionHolder.setConversionService` |
| Request limits | Applies the settings | `SearchRequestLimits.setStructureLimits` |
| Messages | Connects the `MessageSource` of the application | `MessageUtils.init` |
| Persistence unit settings | Hands them over as `EntityManagerFactory` properties | The keys of `SearchableJpaSettings` in the properties |

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

These values belong to the whole process, so set them once when the application starts.

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

Add the map to the JPA properties when the `EntityManagerFactory` is built. A key that is not set uses its default.

- A controller calls `SearchableParamsParser` itself, in the `Map` form of [Writing requests](./request-formats.md).
- `TimeBucketCounter` and `SearchAggregator` are created with an `EntityManager`.
- The answer to an invalid request is built by an exception handler of the application.
- The `epoch_millis` function of the time buckets is registered through the service loader of Hibernate, so it applies without the starter as well.

## Several persistence units

Page sizes, the IN batch, the write chunk and the ceilings of aggregates and buckets are read per persistence unit. The starter hands the values to the default unit Spring Boot builds only, so set the persistence unit properties of [Settings](./auto-configuration.md) on a unit you configure yourself. The time zone, the request limits and the case of patterns exist once per process.

## Jackson 2 and Jackson 3

The JSON helpers of `SearchCondition` (`fromJson`, `toJson`) use Jackson 2. A typed `SearchCondition` is deserialized by Jackson 2 and by Jackson 3, with the same DTO validation. Spring Boot 3 converts an HTTP body with Jackson 2.

## Next step

The [API reference](./api-reference.md) lists every type and method. When something behaves differently than expected, see [Troubleshooting](./faq.md).
