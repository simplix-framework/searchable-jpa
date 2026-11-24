package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestOrderItemSearchDTO;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.dto.TestPostToManySortDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestIdClassEntity;
import dev.simplecore.searchable.test.entity.TestOrderItem;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestIdClassEntityService;
import dev.simplecore.searchable.test.service.TestOrderItemService;
import dev.simplecore.searchable.test.service.TestPostService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A scroll has to return the rows of the search once each, in the order of its sort, whatever the
 * sort values and the key look like. Each case scrolls to the end and compares the windows, laid
 * end to end, with the order worked out in plain Java.
 */
@SpringBootTest
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class ScrollWithSearchTest {

    private record Row(String title, TestPostStatus status, String author, long views) {
    }

    private static final List<Row> ROWS = List.of(
            new Row("a", TestPostStatus.PUBLISHED, "kim", 5),
            new Row("b", null, "lee", 5),
            new Row("c", TestPostStatus.DRAFT, "kim", 7),
            new Row("d", null, "park", 1),
            new Row("e", TestPostStatus.PUBLISHED, "lee", 7),
            new Row("f", TestPostStatus.ARCHIVED, "park", 5),
            new Row("g", null, "kim", 9),
            new Row("h", TestPostStatus.DRAFT, "lee", 1));

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
        for (String name : List.of("kim", "lee", "park")) {
            em.persist(TestAuthor.builder().name(name).email(name + "@example.com").nickname(name).build());
        }
        em.flush();
        Function<String, TestAuthor> authorNamed = name -> em.createQuery(
                "select a from TestAuthor a where a.name = :name", TestAuthor.class).setParameter("name", name).getSingleResult();
        for (Row row : ROWS) {
            em.persist(TestPost.builder().title(row.title()).status(row.status()).viewCount(row.views())
                    .author(authorNamed.apply(row.author())).build());
        }
        for (long line = 1; line <= 7; line++) {
            TestOrderItem item = new TestOrderItem();
            item.setOrderItemId(new TestOrderItem.OrderItemKey("W" + (line % 2), line));
            item.setName("item-" + (line % 3));
            em.persist(item);

            TestIdClassEntity entity = new TestIdClassEntity();
            entity.setTenantId("tenant-" + (line % 2));
            entity.setEntityId(line);
            entity.setName("row-" + (line % 3));
            em.persist(entity);
        }
        em.flush();
        em.clear();
    }

    /** Scrolls to the end, handing each position on as the text a client would send back. */
    private List<String> titlesOfEveryWindow(SearchCondition<TestPostSearchDTO> condition) {
        List<String> titles = new ArrayList<>();
        ScrollPosition position = postService.scrollPosition(null);
        for (int guard = 0; guard <= ROWS.size(); guard++) {
            Window<TestPost> window = postService.scrollWithSearch(condition, position);
            window.forEach(post -> titles.add(post.getTitle()));
            if (!window.hasNext()) {
                return titles;
            }
            position = postService.scrollPosition(SearchCursor.encode(window.positionAt(window.size() - 1)));
        }
        throw new AssertionError("the scroll did not end: " + titles);
    }

    private static List<String> titles(Comparator<Row> order) {
        return ROWS.stream().sorted(order.thenComparing(ROWS::indexOf)).map(Row::title).toList();
    }

    @Test
    @DisplayName("without a sort the windows follow the primary key")
    void byPrimaryKey() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class).size(3).build();

        assertThat(titlesOfEveryWindow(condition)).containsExactlyElementsOf(ROWS.stream().map(Row::title).toList());
    }

    @Test
    @DisplayName("rows sharing a sort value are told apart by the primary key")
    void bySharedValues() {
        SearchCondition<TestPostSearchDTO> ascending = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .sort(s -> s.asc("viewCount")).size(3).build();
        SearchCondition<TestPostSearchDTO> descending = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .sort(s -> s.desc("viewCount")).size(3).build();

        assertThat(titlesOfEveryWindow(ascending)).containsExactlyElementsOf(titles(Comparator.comparingLong(Row::views)));
        assertThat(titlesOfEveryWindow(descending))
                .containsExactlyElementsOf(titles(Comparator.comparingLong(Row::views).reversed()));
    }

    @Test
    @DisplayName("a missing sort value is the smallest value, ascending and descending")
    void byAValueThatCanBeMissing() {
        // The sort is on the entity path, which the search DTO does not offer for sorting.
        Comparator<Row> missingFirst = Comparator.comparing(Row::status, Comparator.nullsFirst(Comparator.comparing(Enum::name)));
        for (boolean ascending : List.of(true, false)) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class).size(3).build();
            SearchCondition.Sort sort = new SearchCondition.Sort();
            sort.addOrder(new SearchCondition.Order("status", ascending ? SearchCondition.Direction.ASC : SearchCondition.Direction.DESC, "status"));
            condition.setSort(sort);

            assertThat(titlesOfEveryWindow(condition)).as(ascending ? "ascending" : "descending")
                    .containsExactlyElementsOf(titles(ascending ? missingFirst : missingFirst.reversed()));
        }
    }

    @Test
    @DisplayName("a sort through a to-one association scrolls like any other")
    void byAnAssociation() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.greaterThan("viewCount", 1L))
                .sort(s -> {
                    s.desc("authorName");
                    s.asc("viewCount");
                }).size(2).build();

        assertThat(titlesOfEveryWindow(condition)).containsExactlyElementsOf(ROWS.stream().filter(row -> row.views() > 1)
                .sorted(Comparator.comparing(Row::author).reversed().thenComparingLong(Row::views).thenComparing(ROWS::indexOf))
                .map(Row::title).toList());
    }

    @Test
    @DisplayName("a backward position returns the rows before it, in the order of the sort")
    void backward() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .sort(s -> s.asc("viewCount")).size(3).build();
        List<String> all = titles(Comparator.comparingLong(Row::views));
        Window<TestPost> first = postService.scrollWithSearch(condition, ScrollPosition.keyset());
        Window<TestPost> second = postService.scrollWithSearch(condition, first.positionAt(first.size() - 1));

        KeysetScrollPosition before = ((KeysetScrollPosition) second.positionAt(0)).backward();
        Window<TestPost> again = postService.scrollWithSearch(condition, postService.scrollPosition(SearchCursor.encode(before)));

        assertThat(again.getContent()).extracting(TestPost::getTitle).containsExactlyElementsOf(all.subList(0, 3));
    }

    @Test
    @DisplayName("an @IdClass entity scrolls by its sort and both parts of its key")
    void idClassEntity() {
        SearchCondition<TestIdClassEntity> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition(null, "name", SearchOperator.STARTS_WITH, "row-", null, "name"));
        SearchCondition.Sort sort = new SearchCondition.Sort();
        sort.addOrder(new SearchCondition.Order("name", SearchCondition.Direction.DESC, "name"));
        condition.setSort(sort);
        condition.setSize(2);

        List<String> seen = new ArrayList<>();
        ScrollPosition position = idClassService.scrollPosition(null);
        for (int guard = 0; guard < 8; guard++) {
            Window<TestIdClassEntity> window = idClassService.scrollWithSearch(condition, position);
            window.forEach(row -> seen.add(row.getName() + "/" + row.getTenantId() + "/" + row.getEntityId()));
            if (!window.hasNext()) {
                break;
            }
            position = idClassService.scrollPosition(SearchCursor.encode(window.positionAt(window.size() - 1)));
        }

        List<String> expected = new ArrayList<>();
        for (long line = 1; line <= 7; line++) {
            expected.add("row-" + (line % 3) + "/tenant-" + (line % 2) + "/" + line);
        }
        expected.sort(Comparator.comparing((String row) -> row.split("/")[0]).reversed()
                .thenComparing(row -> row.split("/")[1]).thenComparing(row -> Long.parseLong(row.split("/")[2])));
        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("an entity with an embedded identifier scrolls by offset and refuses a keyset position")
    void embeddedIdEntity() {
        SearchCondition<TestOrderItemSearchDTO> condition = SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                .sort(s -> s.asc("name")).size(3).build();

        assertThatThrownBy(() -> orderItemService.scrollWithSearch(condition, ScrollPosition.keyset()))
                .isInstanceOf(SearchableOperationException.class).hasMessageContaining("TestOrderItem");

        List<String> seen = new ArrayList<>();
        ScrollPosition position = ScrollPosition.offset();
        for (int guard = 0; guard < 8; guard++) {
            Window<TestOrderItem> window = orderItemService.scrollWithSearch(condition, position);
            window.forEach(item -> seen.add(item.getName() + "/" + item.getOrderItemId().getWarehouseCode() + "/" + item.getOrderItemId().getLineNo()));
            if (!window.hasNext()) {
                break;
            }
            position = orderItemService.scrollPosition(SearchCursor.encode(window.positionAt(window.size() - 1)));
        }

        List<String> expected = new ArrayList<>();
        for (long line = 1; line <= 7; line++) {
            expected.add("item-" + (line % 3) + "/W" + (line % 2) + "/" + line);
        }
        expected.sort(Comparator.comparing((String row) -> row.split("/")[0])
                .thenComparing(row -> row.split("/")[1]).thenComparing(row -> Long.parseLong(row.split("/")[2])));
        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("a position that does not belong to the request is refused as invalid input")
    void positionsOfAnotherRequestAreRefused() {
        SearchCondition<TestPostSearchDTO> byViews = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .sort(s -> s.asc("viewCount")).size(3).build();
        SearchCondition<TestPostSearchDTO> byKey = SearchConditionBuilder.create(TestPostSearchDTO.class).size(3).build();
        Window<TestPost> first = postService.scrollWithSearch(byViews, ScrollPosition.keyset());

        assertThatThrownBy(() -> postService.scrollWithSearch(byKey, first.positionAt(0)))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> postService.scrollWithSearch(byViews, ScrollPosition.forward(Map.of("postId", 1L))))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> postService.scrollPosition("not a cursor"))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> postService.scrollPosition(SearchCursor.encode(ScrollPosition.forward(Map.of("noSuchField", 1L)))))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> postService.scrollPosition(SearchCursor.encode(ScrollPosition.forward(Map.of("viewCount", "many")))))
                .isInstanceOf(SearchableValidationException.class);
    }

    @Test
    @DisplayName("a sort through a collection cannot be scrolled")
    void aSortThroughACollectionIsRefused() {
        SearchCondition<TestPostToManySortDTO> condition = SearchConditionBuilder.create(TestPostToManySortDTO.class)
                .sort(s -> s.asc("commentContent")).size(3).build();

        assertThatThrownBy(() -> postService.scrollWithSearch(condition, ScrollPosition.keyset()))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("commentContent");
    }
}
