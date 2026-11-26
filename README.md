# Searchable JPA

Searching, sorting and paging for Spring Data JPA, controlled by a DTO: an application declares what a client may search, and the library runs only the requests that fit the declaration.

[English documentation](https://simplix-framework.github.io/searchable-jpa/#/en/) · [한국어 문서](https://simplix-framework.github.io/searchable-jpa/#/ko/) · [Changes](docs/en/changelog.md)

## Compatibility

| Library | Spring Boot | Branch |
| --- | --- | --- |
| 2.x | 4.1.x and later 4.x | `master` |
| 1.x | 3.2.x-3.5.x | `spring-boot-3x` |
| 0.1.x | 2.7.x | `spring-boot-2x` (unmaintained) |

This tree builds **2.1.3-SNAPSHOT**, tested with Java 17, 21 and 25, Spring Boot 4.1.2 and Hibernate 7.4.14.Final. Keep all library modules on the same version.

## Quick start

Add the starter. Published versions come from GitHub Packages; [Installation](docs/en/installation.md) ([한국어](docs/ko/installation.md)) covers the repository and a local install.

```groovy
dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:2.1.3-SNAPSHOT'
}
```

Declare the fields a client may search and sort by.

<!-- snippet: PostSearchDTO.java#basic -->
```java
public class PostSearchDTO {

    @SearchableField(operators = {EQUALS, IN}, sortable = true)
    private Long id;

    @SearchableField(operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true, keyword = true)
    @Schema(description = "Post title", example = "Spring")
    @Size(min = 2, max = 100)
    private String title;

    @SearchableField(operators = {EQUALS, IN}, aggregatable = true)
    private PostStatus status;

    @SearchableField(operators = {GREATER_THAN_OR_EQUAL_TO, BETWEEN}, sortable = true, aggregatable = true)
    private Long viewCount;

    @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
    private Instant createdAt;
}
```

Extend the service and take the search as a controller parameter.

<!-- snippet: PostService.java#service -->
```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {

    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

<!-- snippet: PostController.java#controller -->
```java
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    @GetMapping
    public Page<PostResponse> list(@SearchableParams SearchCondition<PostSearchDTO> condition) {
        return service.findAllWithSearch(condition).map(PostResponse::from);
    }

    @PostMapping("/search")
    public Page<PostResponse> search(@RequestBody SearchCondition<PostSearchDTO> condition) {
        return service.findAllWithSearch(condition).map(PostResponse::from);
    }
}
```

<!-- snippet: first-search.http -->
```http
GET /api/posts?title.contains=Spring&sort=createdAt.desc&page=0&size=20
```

[First search API](docs/en/basic-usage.md) ([한국어](docs/ko/basic-usage.md)) builds this example step by step.

## Documentation

The pages follow the order of adoption.

| Stage | Pages |
| --- | --- |
| Install and first search | [Installation](docs/en/installation.md), [First search API](docs/en/basic-usage.md) |
| The search contract | [Declaring search fields](docs/en/search-fields.md), [Writing requests](docs/en/request-formats.md), [Operators and values](docs/en/search-operators.md) |
| Related data | [Searching related data](docs/en/relationship-and-two-phase-query.md) |
| Reading results | [Ways to read](docs/en/reading-results.md) |
| Server control | [Server scope and limits](docs/en/server-scope-and-limits.md) |
| Writes and figures | [Bulk update and delete](docs/en/bulk-write.md), [Aggregates](docs/en/aggregates.md), [Time buckets](docs/en/time-buckets.md) |
| API documents and errors | [OpenAPI](docs/en/openapi-integration.md), [Errors and messages](docs/en/errors-and-messages.md) |
| Operations | [Settings](docs/en/auto-configuration.md), [Query strategy and performance](docs/en/two-phase-query-optimization.md), [Extension and integration](docs/en/advanced-features.md) |
| Reference | [API reference](docs/en/api-reference.md), [Troubleshooting](docs/en/faq.md), [Upgrade guide](docs/en/upgrade.md) |

## Build and verify

Use Java 17 or a supported newer JDK and the checked-in Gradle wrapper.

```bash
./gradlew check performanceTest
npm ci
npm run test:docs
npm run test:docs:e2e
./scripts/build-docs.sh
```

Test CSV fixtures are generated during the build, so a fresh checkout needs no prepared dataset. The documentation checks need Node.js 22 or later, and the e2e check a browser runtime. The [documentation guide](docs/en/documentation-guide.md) ([한국어](docs/ko/documentation-guide.md)) explains listings, figures, the feature ledger, the local preview and versioned deployment.

Patch changes must reach every maintained branch. Use `Backport: spring-boot-3x` / `Forwardport: master` commit trailers and `git cherry-pick -x`, and run `scripts/branch-sync/check.sh --local` before pushing. The release workflow checks the build, publishes packages, creates a GitHub release and advances the development version.

## License

[SimpleCORE License 1.0](LICENSE) applies. See the full terms before distributing or hosting the software. Licensing inquiries: [license@simplecore.kr](mailto:license@simplecore.kr).
