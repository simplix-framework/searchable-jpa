package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Aggregate;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.AggregateValue;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Op;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.ValueCount;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestPostStatsDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Figures asked for by the field names of a search DTO: only the fields the DTO offers for
 * aggregation are computed, under the entity path the DTO maps them to.
 */
@SpringBootTest
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class AggregateAllowListTest {

    @Autowired
    private EntityManager em;

    @BeforeEach
    void loadFixture() {
        for (String table : List.of("test_comment", "test_post_tag", "test_post", "test_author")) {
            em.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        TestAuthor author = TestAuthor.builder().name("writer").email("writer@example.com").nickname("w").build();
        em.persist(author);
        long views = 1;
        for (TestPostStatus status : List.of(TestPostStatus.PUBLISHED, TestPostStatus.PUBLISHED, TestPostStatus.DRAFT)) {
            em.persist(TestPost.builder().title("secret-" + views).status(status).viewCount(views * 10).author(author).build());
            views++;
        }
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("a field the DTO offers is computed under its entity path and named as the client named it")
    void offeredFieldsAreComputed() {
        SearchAggregator aggregator = new SearchAggregator(em);
        SearchCondition<TestPostStatsDTO> published = SearchConditionBuilder.create(TestPostStatsDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED)).build();

        List<AggregateValue> figures = aggregator.aggregate(TestPost.class, published, null, TestPostStatsDTO.class,
                List.of(new Aggregate("views", Op.SUM), new Aggregate("views", Op.MAX)));
        List<ValueCount> perStatus = aggregator.countByValue(TestPost.class, new SearchCondition<>(), null,
                TestPostStatsDTO.class, "status");

        assertThat(figures).extracting(AggregateValue::field, AggregateValue::op, value -> value.value().longValue())
                .containsExactly(tuple("views", "sum", 30L), tuple("views", "max", 20L));
        assertThat(perStatus).extracting(ValueCount::value, ValueCount::count)
                .contains(tuple(TestPostStatus.PUBLISHED, 2L), tuple(TestPostStatus.DRAFT, 1L));
    }

    @Test
    @DisplayName("a field the DTO does not offer, or does not have, is refused before any query runs")
    void otherFieldsAreRefused() {
        SearchAggregator aggregator = new SearchAggregator(em);

        for (String field : List.of("title", "viewCount", "author", "nope")) {
            assertThatThrownBy(() -> aggregator.countByValue(TestPost.class, new SearchCondition<>(), null,
                    TestPostStatsDTO.class, field)).as(field)
                    .isInstanceOf(SearchableValidationException.class).hasMessageContaining(field);
            assertThatThrownBy(() -> aggregator.aggregate(TestPost.class, new SearchCondition<>(), null,
                    TestPostStatsDTO.class, List.of(new Aggregate(field, Op.MAX)))).as(field)
                    .isInstanceOf(SearchableValidationException.class).hasMessageContaining(field);
        }
    }
}
