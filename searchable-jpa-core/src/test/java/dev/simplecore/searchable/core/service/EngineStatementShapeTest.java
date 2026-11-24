package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.utils.EntityKey;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestOrderItemSearchDTO;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestIdClassEntity;
import dev.simplecore.searchable.test.entity.TestOrderItem;
import dev.simplecore.searchable.test.entity.TestOrderNote;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestIdClassEntityService;
import dev.simplecore.searchable.test.service.TestOrderItemService;
import dev.simplecore.searchable.test.service.TestPostService;
import dev.simplecore.searchable.test.support.TestSqlCapture;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The statements a search sends: no row multiplication to undo, no collection joined that nothing
 * filters, and statement sizes that follow the persistence unit's settings.
 */
@SpringBootTest(properties = {"searchable.test.h2-url=jdbc:h2:mem:engine-shape;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.properties.searchable.in_clause_batch_size=4",
        "spring.jpa.properties.searchable.write_chunk_size=2"})
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class EngineStatementShapeTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private TestPostService postService;
    @Autowired
    private TestOrderItemService orderItemService;
    @Autowired
    private TestIdClassEntityService idClassService;

    @BeforeEach
    void loadFixture() {
        SearchableFieldUtils.clearCache();
        for (String table : List.of("test_comment", "test_post_tag", "test_post", "test_author",
                "test_order_note", "test_order_item", "test_id_class_entity")) {
            em.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        TestAuthor author = TestAuthor.builder().name("writer").email("writer@example.com").nickname("w").build();
        em.persist(author);
        for (String title : List.of("plain", "a[1]b", "a1b")) {
            TestPost post = TestPost.builder().title(title).status(TestPostStatus.PUBLISHED).viewCount(1L).author(author).build();
            em.persist(post);
            for (String content : List.of("first", "second")) {
                TestComment comment = new TestComment();
                comment.setContent(content);
                comment.setAuthor(author);
                comment.setPost(post);
                em.persist(comment);
            }
        }
        for (long line = 1; line <= 5; line++) {
            TestOrderItem item = new TestOrderItem();
            item.setOrderItemId(new TestOrderItem.OrderItemKey("W" + (line % 2), line));
            item.setName("item-" + line);
            TestOrderNote note = new TestOrderNote();
            note.setContent("note-" + line);
            item.addNote(note);
            em.persist(item);

            TestIdClassEntity row = new TestIdClassEntity();
            row.setTenantId("tenant-" + (line % 2));
            row.setEntityId(line);
            row.setName("row-" + line);
            em.persist(row);
        }
        em.flush();
        em.clear();
    }

    private static SearchCondition<TestPostSearchDTO> posts(Consumer<FirstCondition> where) {
        return SearchConditionBuilder.create(TestPostSearchDTO.class).where(where::accept).build();
    }

    private List<String> statementsOf(Runnable work) {
        TestSqlCapture.start();
        try {
            work.run();
        } finally {
            TestSqlCapture.stop();
        }
        return TestSqlCapture.captured().stream().map(sql -> sql.toLowerCase(Locale.ROOT)).toList();
    }

    @Test
    @DisplayName("a count over the entity's own fields is a plain count")
    void countOverOwnFieldsIsPlain() {
        List<String> statements = statementsOf(() ->
                assertThat(postService.countWithSearch(posts(w -> w.equals("status", TestPostStatus.PUBLISHED)))).isEqualTo(3));

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).doesNotContain("exists").doesNotContain("join").doesNotContain("distinct");
    }

    @Test
    @DisplayName("a count through a collection tests existence and removes no duplicates")
    void countThroughACollectionTestsExistence() {
        List<String> statements = statementsOf(() ->
                assertThat(postService.countWithSearch(posts(w -> w.contains("commentContent", "s")))).isEqualTo(3));

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).contains("exists").doesNotContain("distinct");
    }

    @Test
    @DisplayName("finding one entity joins no collection that nothing filters")
    void findOneJoinsNoUnfilteredCollection() {
        List<String> statements = statementsOf(() ->
                assertThat(postService.findOneWithSearch(posts(w -> w.equals("searchTitle", "plain")))).isPresent());

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).contains("test_author")
                .doesNotContain("test_comment").doesNotContain("test_post_tag").doesNotContain("distinct");
    }

    @Test
    @DisplayName("finding one entity through a collection it matches twice still finds one")
    void findOneThroughACollectionMatchedTwice() {
        assertThat(postService.findOneWithSearch(posts(w -> w.equals("searchTitle", "plain").contains("commentContent", "s")))
                .map(TestPost::getTitle)).contains("plain");
    }

    @Test
    @DisplayName("a bracket in a pattern value is matched literally")
    void bracketIsMatchedLiterally() {
        Page<TestPost> page = postService.findAllWithSearch(posts(w -> w.contains("searchTitle", "[1]")));

        assertThat(page.getContent()).extracting(TestPost::getTitle).containsExactly("a[1]b");
    }

    @Test
    @DisplayName("a composite key takes its share of a statement's identifiers")
    void compositeKeysShareTheStatementBudget() {
        SearchCondition<TestOrderItemSearchDTO> all = SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                .sort(s -> s.asc("lineNo")).page(0).size(5).build();
        List<TestOrderItem> loaded = new ArrayList<>();

        List<String> statements = statementsOf(() -> loaded.addAll(orderItemService.findAllWithSearch(all).getContent()));

        assertThat(loaded).extracting(TestOrderItem::getName)
                .containsExactly("item-1", "item-2", "item-3", "item-4", "item-5");
        // Four identifiers a statement, two columns a key: five keys take three loading statements.
        assertThat(statements.stream().filter(sql -> sql.contains("test_order_note"))).hasSize(3);
        assertThat(statements).noneMatch(sql -> sql.contains("distinct"));
    }

    @Test
    @DisplayName("a search-based update visits every row once, in chunks of the configured size")
    void updateVisitsEveryRowInChunks() {
        SearchCondition<TestIdClassEntity> everything = new SearchCondition<>();
        everything.getNodes().add(new SearchCondition.Condition(
                null, "name", SearchOperator.STARTS_WITH, "row-", null, "name"));
        TestIdClassEntity change = new TestIdClassEntity();
        change.setDescription("changed");

        List<String> statements = statementsOf(() ->
                assertThat(idClassService.updateWithSearch(everything, change)).isEqualTo(5));

        // Five rows in chunks of two: two full chunks and the last one.
        List<String> selects = statements.stream().filter(sql -> sql.contains("from test_id_class_entity")).toList();
        assertThat(selects).hasSize(3).noneMatch(sql -> sql.contains("distinct"));
        em.clear();
        assertThat(em.createQuery("select e.description from TestIdClassEntity e", String.class).getResultList())
                .hasSize(5).containsOnly("changed");
    }

    @Test
    @DisplayName("the key of an entity is read from the metamodel, in declared order")
    void keysAreReadFromTheMetamodel() {
        assertThat(EntityKey.of(em, TestPost.class)).isEqualTo(new EntityKey("postId", false, List.of()));
        assertThat(EntityKey.of(em, TestOrderItem.class))
                .isEqualTo(new EntityKey("orderItemId", true, List.of("warehouseCode", "lineNo")));
        assertThat(EntityKey.of(em, TestIdClassEntity.class))
                .isEqualTo(new EntityKey(null, false, List.of("tenantId", "entityId")));

        assertThat(EntityKey.of(em, TestOrderItem.class).paths())
                .containsExactly("orderItemId.warehouseCode", "orderItemId.lineNo");
        assertThat(EntityKey.of(em, TestIdClassEntity.class).paths()).containsExactly("tenantId", "entityId");

        TestOrderItem item = em.find(TestOrderItem.class, new TestOrderItem.OrderItemKey("W1", 3L));
        assertThat(EntityKey.of(em, TestOrderItem.class).values(em, TestOrderItem.class, item)).containsExactly("W1", 3L);
        TestIdClassEntity row = em.find(TestIdClassEntity.class, new TestIdClassEntity.CompositeKey("tenant-0", 4L));
        assertThat(EntityKey.of(em, TestIdClassEntity.class).values(em, TestIdClassEntity.class, row))
                .containsExactly("tenant-0", 4L);
    }

    @Test
    @DisplayName("a class that is not an entity has no key to guess")
    void aNonEntityHasNoKey() {
        assertThatThrownBy(() -> EntityKey.of(em, TestPostSearchDTO.class)).isInstanceOf(IllegalArgumentException.class);
    }
}
