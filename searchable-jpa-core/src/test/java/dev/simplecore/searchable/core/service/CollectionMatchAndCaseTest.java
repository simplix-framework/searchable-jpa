package dev.simplecore.searchable.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.utils.SearchablePatternMatchingHolder;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestBundleSearchDTO;
import dev.simplecore.searchable.test.dto.TestPostQuantifierDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestBundle;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestBundleService;
import dev.simplecore.searchable.test.service.TestPostService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a field declares about how it is compared: with or without regard to case, and against the
 * elements of a collection together, one at a time, or not at all. Each question is asked by query
 * parameters, by JSON and by the builder, because the declaration has to reach the query on every
 * path a condition arrives by.
 */
@SpringBootTest
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class CollectionMatchAndCaseTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private TestBundleService bundles;
    @Autowired
    private TestPostService posts;

    @BeforeEach
    void loadFixture() {
        for (String table : List.of("test_bundle_label", "test_bundle", "test_comment", "test_post_tag", "test_post", "test_author")) {
            em.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        bundle("Pro Monthly", "red", "Blue");
        bundle("PRO yearly", "blue");
        bundle("Free");
        bundle("pro trial", "red", "green");

        Map<String, TestAuthor> authors = new HashMap<>();
        for (String name : List.of("kim", "lee", "park")) {
            TestAuthor author = TestAuthor.builder().name(name).email(name + "@example.com").nickname(name).build();
            em.persist(author);
            authors.put(name, author);
        }
        post("first", authors, "kim", "good:lee", "bad:park");
        post("second", authors, "lee", "good:park");
        post("third", authors, "park");
        post("fourth", authors, "kim", "bad:lee", "fine:lee");
        em.flush();
        em.clear();
    }

    @AfterEach
    void restorePatternOption() {
        SearchablePatternMatchingHolder.setCaseInsensitive(false);
    }

    private void bundle(String name, String... labels) {
        TestBundle bundle = new TestBundle();
        bundle.setName(name);
        bundle.getLabels().addAll(List.of(labels));
        em.persist(bundle);
    }

    private void post(String title, Map<String, TestAuthor> authors, String author, String... remarks) {
        TestPost post = TestPost.builder().title(title).status(TestPostStatus.PUBLISHED).viewCount(0L).author(authors.get(author)).build();
        em.persist(post);
        for (String remark : remarks) {
            TestComment comment = new TestComment();
            comment.setContent(remark.split(":")[0]);
            comment.setAuthor(authors.get(remark.split(":")[1]));
            comment.setPost(post);
            em.persist(comment);
        }
    }

    private static Map<String, String> params(String... keysAndValues) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            params.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return params;
    }

    private static <D> SearchCondition<D> json(Class<D> dto, String conditions) {
        try {
            return SearchCondition.fromJson("{\"conditions\":[" + conditions.replace('\'', '"') + "]}", dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Asks one question about the bundles on every path and checks the page, the total and the count. */
    private void bundlesMatching(Map<String, String> params, String json, Consumer<FirstCondition> built, String... expected) {
        List<SearchCondition<TestBundleSearchDTO>> asked = List.of(
                new SearchableParamsParser<>(TestBundleSearchDTO.class).convert(params),
                json(TestBundleSearchDTO.class, json),
                SearchConditionBuilder.create(TestBundleSearchDTO.class).where(built::accept).build());
        for (int path = 0; path < asked.size(); path++) {
            Page<TestBundle> page = bundles.findAllWithSearch(asked.get(path));
            assertThat(page.getContent()).as("%s, path %d", params, path).extracting(TestBundle::getName)
                    .containsExactlyInAnyOrder(expected);
            assertThat(page.getTotalElements()).as("%s, path %d: total", params, path).isEqualTo(expected.length);
            assertThat(bundles.countWithSearch(asked.get(path))).as("%s, path %d: count", params, path).isEqualTo(expected.length);
        }
    }

    private void postsMatching(Map<String, String> params, String json, Consumer<FirstCondition> built, String... expected) {
        List<SearchCondition<TestPostQuantifierDTO>> asked = new ArrayList<>();
        // Query parameters are joined with AND; a question with OR has no such form.
        if (params != null) {
            asked.add(new SearchableParamsParser<>(TestPostQuantifierDTO.class).convert(params));
        }
        asked.add(json(TestPostQuantifierDTO.class, json));
        asked.add(SearchConditionBuilder.create(TestPostQuantifierDTO.class).where(built::accept).build());
        for (int path = 0; path < asked.size(); path++) {
            Page<TestPost> page = posts.findAllWithSearch(asked.get(path));
            assertThat(page.getContent()).as("%s, path %d", params, path).extracting(TestPost::getTitle)
                    .containsExactlyInAnyOrder(expected);
            assertThat(page.getTotalElements()).as("%s, path %d: total", params, path).isEqualTo(expected.length);
            assertThat(posts.countWithSearch(asked.get(path))).as("%s, path %d: count", params, path).isEqualTo(expected.length);
        }
    }

    @Test
    @DisplayName("a field compares as the database does unless it says otherwise")
    void byDefaultTheDatabaseDecides() {
        bundlesMatching(params("name.equals", "pro monthly"),
                "{'field':'name','searchOperator':'equals','value':'pro monthly'}",
                w -> w.equals("name", "pro monthly"));
        bundlesMatching(params("name.equals", "Pro Monthly"),
                "{'field':'name','searchOperator':'equals','value':'Pro Monthly'}",
                w -> w.equals("name", "Pro Monthly"), "Pro Monthly");
    }

    @Test
    @DisplayName("a field that ignores case does so for equality, membership and patterns alike")
    void aFieldThatIgnoresCase() {
        bundlesMatching(params("nameIgnoringCase.equals", "pro monthly"),
                "{'field':'nameIgnoringCase','searchOperator':'equals','value':'pro monthly'}",
                w -> w.equals("nameIgnoringCase", "pro monthly"), "Pro Monthly");
        bundlesMatching(params("nameIgnoringCase.notEquals", "PRO MONTHLY"),
                "{'field':'nameIgnoringCase','searchOperator':'notEquals','value':'PRO MONTHLY'}",
                w -> w.notEquals("nameIgnoringCase", "PRO MONTHLY"), "PRO yearly", "Free", "pro trial");
        bundlesMatching(params("nameIgnoringCase.in", "PRO MONTHLY,free"),
                "{'field':'nameIgnoringCase','searchOperator':'in','value':['PRO MONTHLY','free']}",
                w -> w.in("nameIgnoringCase", List.of("PRO MONTHLY", "free")), "Pro Monthly", "Free");
        bundlesMatching(params("nameIgnoringCase.notIn", "PRO MONTHLY,free"),
                "{'field':'nameIgnoringCase','searchOperator':'notIn','value':['PRO MONTHLY','free']}",
                w -> w.notIn("nameIgnoringCase", List.of("PRO MONTHLY", "free")), "PRO yearly", "pro trial");
        bundlesMatching(params("nameIgnoringCase.contains", "O M"),
                "{'field':'nameIgnoringCase','searchOperator':'contains','value':'O M'}",
                w -> w.contains("nameIgnoringCase", "O M"), "Pro Monthly");
        bundlesMatching(params("nameIgnoringCase.startsWith", "pRO"),
                "{'field':'nameIgnoringCase','searchOperator':'startsWith','value':'pRO'}",
                w -> w.startsWith("nameIgnoringCase", "pRO"), "Pro Monthly", "PRO yearly", "pro trial");
    }

    @Test
    @DisplayName("a field that keeps case does so even when the application ignores case in patterns")
    void aFieldThatKeepsCase() {
        SearchablePatternMatchingHolder.setCaseInsensitive(true);

        bundlesMatching(params("name.contains", "pro"),
                "{'field':'name','searchOperator':'contains','value':'pro'}",
                w -> w.contains("name", "pro"), "Pro Monthly", "PRO yearly", "pro trial");
        bundlesMatching(params("nameExactly.contains", "pro"),
                "{'field':'nameExactly','searchOperator':'contains','value':'pro'}",
                w -> w.contains("nameExactly", "pro"), "pro trial");
    }

    @Test
    @DisplayName("a collection of plain values is searched like any other collection")
    void anElementCollection() {
        bundlesMatching(params("label.equals", "red"),
                "{'field':'label','searchOperator':'equals','value':'red'}",
                w -> w.equals("label", "red"), "Pro Monthly", "pro trial");
        bundlesMatching(params("label.contains", "lu"),
                "{'field':'label','searchOperator':'contains','value':'lu'}",
                w -> w.contains("label", "lu"), "Pro Monthly", "PRO yearly");
        bundlesMatching(params("label.in", "green,blue"),
                "{'field':'label','searchOperator':'in','value':['green','blue']}",
                w -> w.in("label", List.of("green", "blue")), "PRO yearly", "pro trial");
        bundlesMatching(params("label.isNull", "true"),
                "{'field':'label','searchOperator':'isNull'}",
                w -> w.isNull("label"), "Free");
    }

    @Test
    @DisplayName("joined conditions hold for one element together; conditions on some element hold each on its own")
    void oneElementOrSomeElement() {
        bundlesMatching(params("label.equals", "red", "label.contains", "ree"),
                "{'field':'label','searchOperator':'equals','value':'red'},{'operator':'and','field':'label','searchOperator':'contains','value':'ree'}",
                w -> w.equals("label", "red").contains("label", "ree"));
        bundlesMatching(params("anyLabel.equals", "red", "anyLabel.contains", "ree"),
                "{'field':'anyLabel','searchOperator':'equals','value':'red'},{'operator':'and','field':'anyLabel','searchOperator':'contains','value':'ree'}",
                w -> w.equals("anyLabel", "red").contains("anyLabel", "ree"), "pro trial");
    }

    @Test
    @DisplayName("a condition on no element also selects the entities without elements")
    void noElement() {
        bundlesMatching(params("noLabel.equals", "RED"),
                "{'field':'noLabel','searchOperator':'equals','value':'RED'}",
                w -> w.equals("noLabel", "RED"), "PRO yearly", "Free");
        bundlesMatching(params("noLabel.contains", "BLU"),
                "{'field':'noLabel','searchOperator':'contains','value':'BLU'}",
                w -> w.contains("noLabel", "BLU"), "Free", "pro trial");
        bundlesMatching(params("noLabel.in", "Green,BLUE"),
                "{'field':'noLabel','searchOperator':'in','value':['Green','BLUE']}",
                w -> w.in("noLabel", List.of("Green", "BLUE")), "Free");
    }

    @Test
    @DisplayName("the same questions about a collection of entities and what lies behind them")
    void aCollectionOfEntities() {
        postsMatching(params("comment.contains", "good", "commentBy.equals", "park"),
                "{'field':'comment','searchOperator':'contains','value':'good'},{'operator':'and','field':'commentBy','searchOperator':'equals','value':'park'}",
                w -> w.contains("comment", "good").equals("commentBy", "park"), "second");
        postsMatching(params("anyComment.contains", "good", "anyCommentBy.equals", "park"),
                "{'field':'anyComment','searchOperator':'contains','value':'good'},{'operator':'and','field':'anyCommentBy','searchOperator':'equals','value':'park'}",
                w -> w.contains("anyComment", "good").equals("anyCommentBy", "park"), "first", "second");
        postsMatching(params("noComment.contains", "bad"),
                "{'field':'noComment','searchOperator':'contains','value':'bad'}",
                w -> w.contains("noComment", "bad"), "second", "third");
        postsMatching(params("noCommentBy.equals", "lee"),
                "{'field':'noCommentBy','searchOperator':'equals','value':'lee'}",
                w -> w.equals("noCommentBy", "lee"), "second", "third");
        postsMatching(params("anyComment.contains", "bad", "noCommentBy.equals", "park"),
                "{'field':'anyComment','searchOperator':'contains','value':'bad'},{'operator':'and','field':'noCommentBy','searchOperator':'equals','value':'park'}",
                w -> w.contains("anyComment", "bad").equals("noCommentBy", "park"), "fourth");
        // Joined conditions and per-element conditions in one tree: the per-element subquery is
        // then nested inside the EXISTS that holds the joined ones.
        postsMatching(params("comment.contains", "bad", "commentBy.equals", "lee", "anyComment.contains", "fine"),
                "{'field':'comment','searchOperator':'contains','value':'bad'},{'operator':'and','field':'commentBy','searchOperator':'equals','value':'lee'},{'operator':'and','field':'anyComment','searchOperator':'contains','value':'fine'}",
                w -> w.contains("comment", "bad").equals("commentBy", "lee").contains("anyComment", "fine"), "fourth");
        postsMatching(params("comment.contains", "good", "noCommentBy.equals", "lee"),
                "{'field':'comment','searchOperator':'contains','value':'good'},{'operator':'and','field':'noCommentBy','searchOperator':'equals','value':'lee'}",
                w -> w.contains("comment", "good").equals("noCommentBy", "lee"), "second");
        postsMatching(null,
                "{'field':'authorName','searchOperator':'equals','value':'park'},{'operator':'or','field':'anyComment','searchOperator':'contains','value':'fine'}",
                w -> w.equals("authorName", "park").orContains("anyComment", "fine"), "third", "fourth");
    }
}
