package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestOrderItemSearchDTO;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.dto.TestPostToManySortDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestOrderItem;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestOrderItemService;
import dev.simplecore.searchable.test.service.TestPostService;
import dev.simplecore.searchable.test.support.TestSqlCapture;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
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
 * A projected search has to send a statement that selects what the projection reads and nothing
 * else, and still return the rows and the total the search itself returns.
 */
@SpringBootTest
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class ProjectWithSearchTest {

    interface Headline {
        Long getPostId();

        String getTitle();
    }

    record Tally(String title, Long viewCount) {
    }

    interface ItemName {
        String getName();
    }

    @Autowired
    private EntityManager em;
    @Autowired
    private TestPostService postService;
    @Autowired
    private TestOrderItemService orderItemService;

    @BeforeEach
    void loadFixture() {
        SearchableFieldUtils.clearCache();
        for (String table : List.of("test_comment", "test_post_tag", "test_post", "test_author",
                "test_order_note", "test_order_item")) {
            em.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        TestAuthor author = TestAuthor.builder().name("writer").email("writer@example.com").nickname("w").build();
        em.persist(author);
        for (int i = 1; i <= 5; i++) {
            TestPost post = TestPost.builder().title("post-" + i).content("long body " + i)
                    .status(i % 2 == 0 ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED).viewCount((long) (10 - i)).author(author).build();
            em.persist(post);
            for (int j = 0; j < i; j++) {
                TestComment comment = new TestComment();
                comment.setContent("remark " + j);
                comment.setAuthor(author);
                comment.setPost(post);
                em.persist(comment);
            }
            TestOrderItem item = new TestOrderItem();
            item.setOrderItemId(new TestOrderItem.OrderItemKey("W" + (i % 2), (long) i));
            item.setName("item-" + i);
            em.persist(item);
        }
        em.flush();
        em.clear();
    }

    private static List<String> lowerCased(List<String> statements) {
        return statements.stream().map(sql -> sql.toLowerCase(Locale.ROOT)).toList();
    }

    @Test
    @DisplayName("an interface projection selects its own columns and nothing of the rest")
    void interfaceProjectionSelectsItsColumns() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED).contains("commentContent", "remark 2"))
                .sort(s -> s.desc("viewCount")).page(0).size(1).build();

        TestSqlCapture.start();
        Page<Headline> page = postService.projectWithSearch(condition, Headline.class);
        TestSqlCapture.stop();

        // Posts 3 and 5 are published and have a third remark; 3 has the more views.
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Headline::getTitle).containsExactly("post-3");
        assertThat(page.getContent().get(0).getPostId()).isNotNull();
        List<String> statements = lowerCased(TestSqlCapture.captured());
        String select = statements.stream().filter(sql -> !sql.contains("count(")).findFirst().orElseThrow();
        String selected = select.substring(0, select.indexOf(" from "));
        assertThat(selected).contains("post_id").contains("title")
                .doesNotContain("content").doesNotContain("author_id").doesNotContain("status");
        assertThat(statements).noneMatch(sql -> sql.contains("test_author")).noneMatch(sql -> sql.contains("distinct"));
    }

    @Test
    @DisplayName("a record projection selects its components")
    void recordProjectionSelectsItsComponents() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .sort(s -> s.asc("viewCount")).page(1).size(2).build();

        TestSqlCapture.start();
        Page<Tally> page = postService.projectWithSearch(condition, Tally.class);
        TestSqlCapture.stop();

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).containsExactly(new Tally("post-3", 7L), new Tally("post-2", 8L));
        String select = lowerCased(TestSqlCapture.captured()).stream().filter(sql -> !sql.contains("count(")).findFirst().orElseThrow();
        assertThat(select.substring(0, select.indexOf(" from "))).contains("title").contains("view_count").doesNotContain("content");
    }

    @Test
    @DisplayName("a projection of a composite-key entity pages in the order of its key")
    void compositeKeyEntity() {
        SearchCondition<TestOrderItemSearchDTO> condition = SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                .page(0).size(3).build();

        Page<ItemName> page = orderItemService.projectWithSearch(condition, ItemName.class);

        // Ordered by warehouse code, then line number.
        assertThat(page.getContent()).extracting(ItemName::getName).containsExactly("item-2", "item-4", "item-1");
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("a sort through a collection cannot be projected")
    void aSortThroughACollectionIsRefused() {
        SearchCondition<TestPostToManySortDTO> condition = SearchConditionBuilder.create(TestPostToManySortDTO.class)
                .sort(s -> s.asc("commentContent")).page(0).size(3).build();

        assertThatThrownBy(() -> postService.projectWithSearch(condition, Headline.class))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("commentContent");
    }
}
