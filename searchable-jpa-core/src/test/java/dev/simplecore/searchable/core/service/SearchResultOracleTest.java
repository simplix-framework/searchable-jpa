package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Aggregate;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Op;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every way of asking the same question has to give the same rows. Each case below is a condition
 * and the plain Java predicate it stands for, each scope a restriction the server adds; the rows
 * both select from the fixture are what paging, scrolling, projecting, counting, existence,
 * first-match and the aggregates must all agree with.
 */
class SearchResultOracleTest extends ArticleSearchFixture {

    private static final int PAGE_SIZE = 4;

    private record Case(String name, Supplier<SearchCondition<?>> condition, Predicate<Article> expected) {

        Case(String name, Consumer<FirstCondition> where, Predicate<Article> expected) {
            this(name, () -> SearchConditionBuilder.create(TestPostSearchDTO.class).where(where::accept).build(), expected);
        }
    }

    /** A condition the search DTO does not offer, addressed by its entity path. */
    private static Supplier<SearchCondition<?>> onPath(String path, SearchOperator operator, Object value) {
        return () -> {
            SearchCondition<TestPost> condition = new SearchCondition<>();
            condition.getNodes().add(new SearchCondition.Condition(null, path, operator, value, null, path));
            return condition;
        };
    }
    private static final List<Case> CASES = List.of(
            new Case("everything", () -> SearchConditionBuilder.create(TestPostSearchDTO.class).build(), article -> true),
            new Case("a negated pattern through a collection holds for some element",
                    onPath("comments.content", SearchOperator.NOT_CONTAINS, "good"),
                    a -> a.remarks().stream().anyMatch(r -> !r.content().contains("good"))),
            new Case("a negated value behind a collection's association holds for some element",
                    onPath("comments.author.name", SearchOperator.NOT_EQUALS, "lee"),
                    a -> a.remarks().stream().anyMatch(r -> !r.author().equals("lee"))),
            new Case("a missing value through a collection selects the entities without elements",
                    onPath("comments.content", SearchOperator.IS_NULL, null),
                    a -> a.remarks().isEmpty()),
            new Case("a present value through a collection selects the entities with elements",
                    onPath("tags.name", SearchOperator.IS_NOT_NULL, null),
                    a -> !a.tags().isEmpty()),
            new Case("a field of the entity itself",
                    w -> w.equals("status", TestPostStatus.PUBLISHED),
                    a -> a.status() == TestPostStatus.PUBLISHED),
            new Case("a to-one path",
                    w -> w.equals("authorName", "kim"),
                    a -> a.author().equals("kim")),
            new Case("a one-to-many path",
                    w -> w.contains("commentContent", "good"),
                    a -> a.remarks().stream().anyMatch(r -> r.content().contains("good"))),
            new Case("a many-to-many path",
                    w -> w.equals("tagName", "java"),
                    a -> a.tags().contains("java")),
            new Case("two conditions on one collection hold for the same element",
                    w -> w.contains("commentContent", "good read").equals("commentAuthorName", "lee"),
                    a -> a.remarks().stream().anyMatch(r -> r.content().contains("good read") && r.author().equals("lee"))),
            new Case("conditions on two different collections",
                    w -> w.contains("commentContent", "good").equals("tagName", "spring"),
                    a -> a.remarks().stream().anyMatch(r -> r.content().contains("good")) && a.tags().contains("spring")),
            new Case("a collection or the entity itself",
                    w -> w.equals("tagName", "legacy").orGreaterThan("viewCount", 200L),
                    a -> a.tags().contains("legacy") || a.views() > 200),
            new Case("a to-one path or a collection",
                    w -> w.equals("authorName", "park").orContains("commentContent", "typo"),
                    a -> a.author().equals("park") || a.remarks().stream().anyMatch(r -> r.content().contains("typo"))),
            new Case("a negation on the entity leaves out rows without a value",
                    w -> w.notEquals("status", TestPostStatus.DRAFT),
                    a -> a.status() != null && a.status() != TestPostStatus.DRAFT),
            new Case("a missing value",
                    w -> w.isNull("status"),
                    a -> a.status() == null),
            new Case("a nested group",
                    w -> w.equals("status", TestPostStatus.PUBLISHED)
                            .and(g -> g.greaterThan("viewCount", 200L).orEquals("authorName", "lee")),
                    a -> a.status() == TestPostStatus.PUBLISHED && (a.views() > 200 || a.author().equals("lee"))),
            new Case("a group reaching into a collection, or a plain condition",
                    w -> w.where(g -> g.equals("authorName", "kim").contains("commentContent", "good"))
                            .or(g -> g.equals("tagName", "legacy")),
                    a -> (a.author().equals("kim") && a.remarks().stream().anyMatch(r -> r.content().contains("good")))
                            || a.tags().contains("legacy")),
            new Case("a membership test on a collection",
                    w -> w.in("status", List.of(TestPostStatus.DRAFT, TestPostStatus.ARCHIVED)).contains("authorName", "e"),
                    a -> (a.status() == TestPostStatus.DRAFT || a.status() == TestPostStatus.ARCHIVED) && a.author().contains("e")),
            new Case("nothing matches",
                    w -> w.equals("authorName", "nobody"),
                    a -> false));

    private record Scope(String name, Specification<TestPost> restriction, Predicate<Article> expected) {
    }

    /** What a view of one column of the entity reads. */
    interface TitleOnly {
        String getTitle();
    }

    private static final List<Scope> SCOPES = List.of(
            new Scope("no scope", null, article -> true),
            new Scope("the entity's own field",
                    (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("viewCount"), 10L),
                    a -> a.views() >= 10),
            new Scope("a to-one association",
                    (root, query, cb) -> cb.equal(root.get("author").get("name"), "kim"),
                    a -> a.author().equals("kim")),
            new Scope("a collection, tested by a subquery",
                    (root, query, cb) -> {
                        Subquery<Integer> remarked = query.subquery(Integer.class);
                        Root<TestComment> comment = remarked.from(TestComment.class);
                        remarked.select(cb.literal(1)).where(cb.equal(comment.get("post"), root),
                                cb.equal(comment.get("author").get("name"), "park"));
                        return cb.exists(remarked);
                    },
                    a -> a.remarks().stream().anyMatch(r -> r.author().equals("park"))),
            new Scope("nothing", (root, query, cb) -> cb.disjunction(), article -> false));
    private static SearchCondition<?> conditionOf(Case testCase) {
        return testCase.condition().get();
    }

    private List<String> everyWindow(SearchCondition<?> condition, Specification<TestPost> scope) {
        List<String> titles = new ArrayList<>();
        condition.setSize(PAGE_SIZE);
        ScrollPosition position = postService.scrollPosition(null);
        while (true) {
            Window<TestPost> window = postService.scrollWithSearch(condition, scope, position);
            window.forEach(post -> titles.add(post.getTitle()));
            if (!window.hasNext()) {
                return titles;
            }
            // The position travels as the text a client would hand back.
            position = postService.scrollPosition(SearchCursor.encode(window.positionAt(window.size() - 1)));
        }
    }

    private List<String> everyProjectedPage(SearchCondition<?> condition, Specification<TestPost> scope, List<Long> totals) {
        List<String> titles = new ArrayList<>();
        for (int pageNumber = 0; ; pageNumber++) {
            condition.setPage(pageNumber);
            condition.setSize(PAGE_SIZE);
            Page<TitleOnly> page = postService.projectWithSearch(condition, scope, TitleOnly.class);
            page.getContent().forEach(view -> titles.add(view.getTitle()));
            totals.add(page.getTotalElements());
            if (!page.hasNext()) {
                return titles;
            }
        }
    }

    private List<String> everyPage(SearchCondition<?> condition, Specification<TestPost> scope, List<Long> totals) {
        List<String> titles = new ArrayList<>();
        for (int pageNumber = 0; ; pageNumber++) {
            condition.setPage(pageNumber);
            condition.setSize(PAGE_SIZE);
            Page<TestPost> page = postService.findAllWithSearch(condition, scope);
            page.getContent().forEach(post -> titles.add(post.getTitle()));
            totals.add(page.getTotalElements());
            if (!page.hasNext()) {
                return titles;
            }
        }
    }

    @Test
    @DisplayName("every way of reading agrees with the condition and the scope together")
    void everyEntryPointAgreesWithThePredicate() {
        SearchAggregator aggregator = new SearchAggregator(em);
        for (Scope scope : SCOPES) {
            for (Case testCase : CASES) {
                String label = testCase.name() + " / " + scope.name();
                Specification<TestPost> restriction = scope.restriction();
                List<Article> expected = ARTICLES.stream().filter(testCase.expected().and(scope.expected())).toList();
                Set<String> expectedTitles = expected.stream().map(Article::title).collect(Collectors.toCollection(TreeSet::new));

                List<Long> totals = new ArrayList<>();
                List<String> paged = everyPage(conditionOf(testCase), restriction, totals);
                assertThat(paged).as("%s: no row twice across pages", label).doesNotHaveDuplicates();
                assertThat(new TreeSet<>(paged)).as("%s: rows", label).isEqualTo(expectedTitles);
                assertThat(totals).as("%s: total on every page", label).containsOnly((long) expected.size());

                em.clear();
                assertThat(everyWindow(conditionOf(testCase), restriction))
                        .as("%s: the windows of a scroll are the pages", label).containsExactlyElementsOf(paged);
                em.clear();
                List<Long> projectedTotals = new ArrayList<>();
                assertThat(everyProjectedPage(conditionOf(testCase), restriction, projectedTotals))
                        .as("%s: the projected pages are the pages", label).containsExactlyElementsOf(paged);
                assertThat(projectedTotals).as("%s: projected total", label).containsOnly((long) expected.size());

                assertThat(postService.countWithSearch(conditionOf(testCase), restriction))
                        .as("%s: count", label).isEqualTo(expected.size());
                assertThat(postService.existsWithSearch(conditionOf(testCase), restriction))
                        .as("%s: exists", label).isEqualTo(!expected.isEmpty());
                assertThat(postService.findFirstWithSearch(conditionOf(testCase), restriction).map(TestPost::getTitle))
                        .as("%s: first", label)
                        .satisfies(first -> assertThat(first.isPresent()).isEqualTo(!expected.isEmpty()))
                        .satisfies(first -> first.ifPresent(title -> assertThat(expectedTitles).contains(title)));
                if (expected.size() == 1) {
                    assertThat(postService.findOneWithSearch(conditionOf(testCase), restriction).map(TestPost::getTitle))
                            .as("%s: the one", label).contains(expected.get(0).title());
                }

                Map<Object, Long> perStatus = new LinkedHashMap<>();
                aggregator.countByValue(TestPost.class, conditionOf(testCase), restriction, "status")
                        .forEach(count -> perStatus.put(count.value(), count.count()));
                for (TestPostStatus status : TestPostStatus.values()) {
                    assertThat(perStatus.get(status)).as("%s: rows in %s", label, status)
                            .isEqualTo(expected.stream().filter(a -> a.status() == status).count());
                }
                assertThat(aggregator.aggregate(TestPost.class, conditionOf(testCase), restriction,
                        List.of(new Aggregate("viewCount", Op.SUM))).get(0).value().longValue())
                        .as("%s: sum of views", label)
                        .isEqualTo(expected.stream().mapToLong(Article::views).sum());
                em.clear();
            }
        }
    }

    @Test
    @DisplayName("a search-based update changes only the rows inside the scope")
    void updateStaysInsideTheScope() {
        Case anyOf = CASES.stream().filter(c -> c.name().equals("a collection or the entity itself")).findFirst().orElseThrow();
        Scope ownAuthor = SCOPES.get(2);
        TestPostDTOs.PostUpdateDTO change = new TestPostDTOs.PostUpdateDTO();
        change.setContent("touched");

        long updated = postService.updateWithSearch(conditionOf(anyOf), ownAuthor.restriction(), change);
        em.flush();
        em.clear();

        List<String> expected = ARTICLES.stream().filter(anyOf.expected().and(ownAuthor.expected())).map(Article::title).toList();
        assertThat(expected).isNotEmpty();
        assertThat(updated).isEqualTo(expected.size());
        // Read in Java: a database cannot compare a large-object column in a where clause.
        assertThat(em.createQuery("select p from TestPost p", TestPost.class).getResultList().stream()
                .filter(post -> "touched".equals(post.getContent())).map(TestPost::getTitle))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("a search-based delete removes only the rows inside the scope")
    void deleteStaysInsideTheScope() {
        Case unremarked = CASES.stream()
                .filter(c -> c.name().equals("a missing value through a collection selects the entities without elements"))
                .findFirst().orElseThrow();
        Scope read = SCOPES.get(1);
        List<String> doomed = ARTICLES.stream().filter(unremarked.expected().and(read.expected())).map(Article::title).toList();
        assertThat(doomed).isNotEmpty().hasSizeLessThan((int) ARTICLES.stream().filter(unremarked.expected()).count());

        long deleted = postService.deleteWithSearch(conditionOf(unremarked), read.restriction());
        em.flush();
        em.clear();

        assertThat(deleted).isEqualTo(doomed.size());
        assertThat(em.createQuery("select p.title from TestPost p", String.class).getResultList())
                .containsExactlyInAnyOrderElementsOf(ARTICLES.stream().map(Article::title).filter(title -> !doomed.contains(title)).toList());
    }

    private static void isAConfigurationError(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(SearchableConfigurationException.class);
    }

    @Test
    @DisplayName("a scope that would repeat rows is refused by every entry point")
    void aScopeThatJoinsACollectionIsRefused() {
        Specification<TestPost> joining = (root, query, cb) -> cb.equal(root.join("comments").get("content"), "good read");
        Specification<TestPost> fetching = (root, query, cb) -> {
            root.fetch("tags");
            return cb.conjunction();
        };
        for (Specification<TestPost> scope : List.of(joining, fetching)) {
            SearchCondition<TestPostSearchDTO> everything = SearchConditionBuilder.create(TestPostSearchDTO.class).build();
            assertThatThrownBy(() -> postService.findAllWithSearch(everything, scope))
                    .satisfies(SearchResultOracleTest::isAConfigurationError);
            assertThatThrownBy(() -> postService.countWithSearch(everything, scope))
                    .satisfies(SearchResultOracleTest::isAConfigurationError);
            assertThatThrownBy(() -> postService.scrollWithSearch(everything, scope, postService.scrollPosition(null)))
                    .satisfies(SearchResultOracleTest::isAConfigurationError);
            assertThatThrownBy(() -> postService.projectWithSearch(everything, scope, TitleOnly.class))
                    .satisfies(SearchResultOracleTest::isAConfigurationError);
            assertThatThrownBy(() -> postService.deleteWithSearch(everything, scope))
                    .satisfies(SearchResultOracleTest::isAConfigurationError);
            assertThatThrownBy(() -> new SearchAggregator(em).countByValue(TestPost.class, everything, scope, "status"))
                    .satisfies(SearchResultOracleTest::isAConfigurationError);
        }
        assertThat(em.createQuery("select count(p) from TestPost p", Long.class).getSingleResult()).isEqualTo(ARTICLES.size());
    }
}
