package dev.simplecore.searchable.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.AggregateValue;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter.TimeBucket;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs what the documentation shows: every request file under {@code docs-examples} is sent to the
 * example application, and the Java listings are called with the rows the documentation describes.
 */
@SpringBootTest(classes = DocsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:docs-examples;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "searchable.web.problem-details.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentationExamplesTest {

    private static final String BASICS = "Spring Boot basics";
    private static final String PAGING = "JPA paging in depth";
    private static final String TUNING = "Query tuning draft";
    private static final String SECURITY = "Spring Security notes";

    /** The titles each request example returns, in order, as the documentation states them. */
    private static final Map<String, List<String>> TITLES = Map.ofEntries(
            Map.entry("first-search", List.of(SECURITY, BASICS)),
            Map.entry("op-comparison", List.of(BASICS, SECURITY, PAGING)),
            Map.entry("op-in", List.of(BASICS, PAGING, TUNING)),
            Map.entry("op-between-date", List.of(BASICS, PAGING)),
            Map.entry("op-null", List.of(TUNING)),
            Map.entry("keyword", List.of(BASICS)),
            Map.entry("sort-multiple", List.of(PAGING, SECURITY)),
            Map.entry("related-author", List.of(BASICS, TUNING)),
            Map.entry("related-sort", List.of(BASICS, TUNING, PAGING, SECURITY)),
            Map.entry("collection-joined", List.of(PAGING)),
            Map.entry("collection-any", List.of(BASICS, PAGING)),
            Map.entry("collection-none", List.of(TUNING, SECURITY)),
            Map.entry("without-tag", List.of(BASICS, PAGING, SECURITY)),
            Map.entry("element-collection", List.of(BASICS, PAGING)),
            Map.entry("raw", List.of(BASICS, SECURITY)),
            Map.entry("record-dto", List.of(BASICS)),
            Map.entry("titles", List.of(BASICS, PAGING)),
            Map.entry("post-group", List.of(PAGING, BASICS)),
            Map.entry("post-values", List.of(BASICS, PAGING, SECURITY)),
            Map.entry("values-get", List.of(BASICS, PAGING, SECURITY)));

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AuthorRepository authors;
    @Autowired
    private TagRepository tags;
    @Autowired
    private PostRepository posts;
    @Autowired
    private PostQueries queries;
    @Autowired
    private PostMaintenance maintenance;
    @Autowired
    private PostStatsController stats;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeAll
    void loadSampleRows() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> SamplePosts.load(authors, tags, posts));
    }

    private static List<Resource> examples(String directory) throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver()
                .getResources("classpath:docs-examples/" + directory + "/*.http");
        return Arrays.stream(found).sorted((a, b) -> name(a).compareTo(name(b))).toList();
    }

    private static String name(Resource resource) {
        String file = resource.getFilename();
        return file == null ? "" : file.substring(0, file.length() - ".http".length());
    }

    private MockHttpServletResponse send(String example) throws Exception {
        List<String> lines = example.lines().toList();
        String[] requestLine = lines.get(0).split(" ", 2);
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .request(HttpMethod.valueOf(requestLine[0]), URI.create(requestLine[1]))
                .locale(Locale.ENGLISH);
        int blank = lines.indexOf("");
        int headerEnd = blank < 0 ? lines.size() : blank;
        for (String header : lines.subList(1, headerEnd)) {
            String[] parts = header.split(":", 2);
            request.header(parts[0].trim(), parts[1].trim());
        }
        if (blank >= 0) {
            request.content(String.join("\n", lines.subList(blank + 1, lines.size())));
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse send(Resource resource) throws Exception {
        return send(resource.getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> titles(JsonNode rows) {
        List<String> titles = new ArrayList<>();
        rows.forEach(row -> titles.add(row.get("title").asText()));
        return titles;
    }

    @Test
    @DisplayName("every request the documentation shows is answered, with the rows it says")
    void requestExamplesAreAnswered() throws Exception {
        List<Resource> examples = examples("requests");
        assertThat(examples).hasSizeGreaterThanOrEqualTo(TITLES.size());
        for (Resource example : examples) {
            MockHttpServletResponse response = send(example);
            assertThat(response.getStatus()).as(name(example)).isEqualTo(200);
            List<String> expected = TITLES.get(name(example));
            if (expected != null) {
                JsonNode body = json.readTree(response.getContentAsString());
                assertThat(titles(body.get("content"))).as(name(example)).isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("every request the documentation shows as refused is answered with status 400")
    void rejectedExamplesAreRefused() throws Exception {
        List<Resource> examples = examples("rejected");
        assertThat(examples).isNotEmpty();
        for (Resource example : examples) {
            assertThat(send(example).getStatus()).as(name(example)).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("a violated constraint is reported in the language of the request")
    void constraintMessagesFollowTheRequestLocale() throws Exception {
        JsonNode problem = json.readTree(send("GET /api/posts?title.contains=a").getContentAsString());
        assertThat(problem.get("detail").asText()).contains("size must be between 2 and 100");
    }

    private static List<String> parameterNames(JsonNode paths, String path) {
        List<String> names = new ArrayList<>();
        paths.path(path).path("get").path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
        return names;
    }

    private static JsonNode parameter(JsonNode paths, String path, String name) {
        for (JsonNode parameter : paths.path(path).path("get").path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("No parameter " + name + " on " + path);
    }

    @Test
    @DisplayName("the API document of the example application is what the OpenAPI page describes")
    void apiDocumentMatchesTheOpenApiPage() throws Exception {
        MockHttpServletResponse response = send("GET /v3/api-docs/posts");
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode paths = json.readTree(response.getContentAsString()).path("paths");

        assertThat(parameterNames(paths, "/api/posts"))
                .contains("title.equals", "title.contains", "title.startsWith", "status.in", "keyword", "sort", "page", "size")
                .doesNotContain("condition", "status.contains");
        assertThat(parameterNames(paths, "/api/posts/raw")).contains("title.contains").doesNotContain("params");
        assertThat(parameterNames(paths, "/api/posts/simple"))
                .containsExactly("title.equals", "title.contains", "status.equals", "page", "size", "sort");

        assertThat(parameter(paths, "/api/posts", "title.contains").path("description").asText()).contains("Post title");
        assertThat(parameter(paths, "/api/posts", "status.equals").path("schema").path("enum"))
                .extracting(JsonNode::asText).containsExactly("DRAFT", "PUBLISHED", "ARCHIVED");
        assertThat(parameter(paths, "/api/posts", "publishedAt.isNull").path("schema").path("enum"))
                .extracting(JsonNode::asBoolean).containsExactly(true);
        assertThat(parameter(paths, "/api/posts", "sort").path("explode").asBoolean(true)).isFalse();
        assertThat(parameter(paths, "/api/posts", "createdAt.greaterThan").path("schema").path("format").asText())
                .isEqualTo("date-time");
        assertThat(paths.path("/api/posts").path("get").path("description").asText()).contains("authorName");
        assertThat(paths.path("/api/posts/search").path("post").path("requestBody").path("content")
                .path("application/json").path("examples").isEmpty()).isFalse();
    }

    private JsonNode stated(String response) throws IOException {
        Resource resource = new PathMatchingResourcePatternResolver()
                .getResource("classpath:docs-examples/responses/" + response + ".json");
        return json.readTree(resource.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("the response bodies the documentation shows are the ones the application sends")
    void responseExamplesAreWhatIsSent() throws Exception {
        assertThat(json.readTree(send("GET /api/posts?status.contains=PUB").getContentAsString()))
                .isEqualTo(stated("operator-not-allowed"));
        assertThat(json.readTree(send("GET /api/posts/stats/status").getContentAsString()))
                .isEqualTo(stated("stats-status"));
        assertThat(json.readTree(send("GET /api/posts/stats/views?status.equals=PUBLISHED").getContentAsString()))
                .isEqualTo(stated("stats-views"));
        assertThat(json.readTree(send("GET /api/posts/counts?from=2026-10-01T00:00:00Z&to=2026-10-08T00:00:00Z"
                + "&buckets=7&status.in=PUBLISHED,ARCHIVED").getContentAsString())).isEqualTo(stated("counts"));
    }

    @Test
    @DisplayName("a scroll walks the rows window by window and ends without a cursor")
    void scrollWalksTheRows() throws Exception {
        JsonNode first = json.readTree(send("GET /api/posts/scroll?sort=createdAt.desc&size=2").getContentAsString());
        assertThat(titles(first.get("items"))).containsExactly(SECURITY, TUNING);
        String cursor = first.get("next").asText();

        JsonNode second = json.readTree(
                send("GET /api/posts/scroll?sort=createdAt.desc&size=2&cursor=" + cursor).getContentAsString());
        assertThat(titles(second.get("items"))).containsExactly(PAGING, BASICS);
        assertThat(second.get("next").isNull()).isTrue();
    }

    @Test
    @DisplayName("the calendar count and the server-side figures are the ones the documentation states")
    void calendarCountsAndServerFigures() {
        List<TimeBucket> perDay = stats.perDay(new SearchCondition<PostSearchDTO>(),
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-04T00:00:00Z"), ZoneId.of("UTC"));
        assertThat(perDay).extracting(TimeBucket::count).containsExactly(1L, 0L, 1L);
        assertThat(perDay.get(0).from()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(perDay.get(0).to()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));

        List<AggregateValue> figures = stats.newestAndTotal(new SearchCondition<PostSearchDTO>());
        assertThat(figures.get(0).result()).isEqualTo(Instant.parse("2026-10-07T09:00:00Z"));
        assertThat(figures.get(1).value().longValue()).isEqualTo(4L);
    }

    @Test
    @DisplayName("the searches the server composes return the rows the documentation states")
    void serverComposedSearches() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(queries.publishedAboutSpringOrJpa().getContent()).extracting(Post::getTitle)
                    .containsExactly(PAGING, BASICS);
            assertThat(queries.popularOrFeatured().getContent()).extracting(Post::getTitle)
                    .containsExactly(BASICS, PAGING, SECURITY);
            assertThat(queries.viewedPublishedOrArchived().getContent()).extracting(Post::getTitle)
                    .containsExactly(BASICS, PAGING, SECURITY);
            assertThat(queries.draftsOrPopular().getContent()).extracting(Post::getTitle)
                    .containsExactly(PAGING, TUNING);
            assertThat(queries.newestWithAuthor().getContent()).extracting(Post::getTitle)
                    .containsExactly(SECURITY, TUNING, PAGING, BASICS);
            assertThat(queries.newestPublished()).map(Post::getTitle).contains(PAGING);
            assertThat(queries.theOnlyDraft()).map(Post::getTitle).contains(TUNING);

            SearchCondition<PostSearchDTO> everything = new SearchCondition<>();
            assertThat(queries.countPublished(everything)).isEqualTo(4);
            assertThat(queries.anyMatch(everything)).isTrue();
            assertThat(queries.publishedOnly(new SearchCondition<>()).getContent()).extracting(Post::getTitle)
                    .containsExactlyInAnyOrder(BASICS, PAGING);
            assertThat(queries.summaries(new SearchCondition<>()).getContent()).extracting(PostSummary::getTitle)
                    .containsExactlyInAnyOrder(BASICS, PAGING, TUNING, SECURITY);
            assertThat(queries.withAuthorAndComments(new SearchCondition<>()).getContent()).hasSize(4);
            assertThat(queries.ofTenant(new SearchCondition<>(), 1L).getContent()).extracting(Post::getTitle)
                    .containsExactlyInAnyOrder(BASICS, PAGING, TUNING);
            SearchCondition<PostSearchDTO> draftOrSpring = SearchConditionBuilder.create(PostSearchDTO.class)
                    .where(w -> w.equals("status", PostStatus.DRAFT).orContains("title", "Spring"))
                    .build();
            assertThat(queries.ofTenant(draftOrSpring, 1L).getContent()).extracting(Post::getTitle)
                    .containsExactlyInAnyOrder(BASICS, TUNING);
        });
    }

    @Test
    @DisplayName("the search-based writes change only the rows inside the scope")
    void writesStayInsideTheScope() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(maintenance.archiveDrafts(1L)).isEqualTo(1);
            assertThat(maintenance.archiveDrafts(2L)).isZero();
            assertThat(maintenance.deleteArchived(2L)).isEqualTo(1);
            assertThat(posts.count()).isEqualTo(3);
            status.setRollbackOnly();
        });
        assertThat(posts.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("the settings listed for an application without the starter are the keys the library reads")
    void coreOnlySettingsAreTheKeysTheLibraryReads() {
        assertThat(CoreOnlySetup.persistenceUnitSettings()).containsOnlyKeys(
                SearchableJpaSettings.DEFAULT_PAGE_SIZE, SearchableJpaSettings.MAX_PAGE_SIZE,
                SearchableJpaSettings.AUTO_FETCH_COLLECTIONS, SearchableJpaSettings.IN_CLAUSE_BATCH_SIZE,
                SearchableJpaSettings.WRITE_CHUNK_SIZE, SearchableJpaSettings.REQUIRE_WRITE_FILTER,
                SearchableJpaSettings.MAX_AGGREGATE_GROUPS, SearchableJpaSettings.MAX_BUCKETS);
    }
}
