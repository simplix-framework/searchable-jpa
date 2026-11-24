package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.entity.WallClockEvent;
import dev.simplecore.searchable.test.service.TestUpdatableEntityService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The limits a persistence unit sets for itself: a search-based write that must be filtered, and
 * ceilings on what one aggregate or one time strip may return.
 */
@SpringBootTest(properties = {"searchable.test.h2-url=jdbc:h2:mem:unit-limits;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.properties.searchable.require_write_filter=true",
        "spring.jpa.properties.searchable.max_aggregate_groups=2",
        "spring.jpa.properties.searchable.max_buckets=4"})
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class WriteFilterGuardTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private TestUpdatableEntityService service;

    @BeforeEach
    void loadFixture() {
        em.createQuery("delete from TestUpdatableEntity").executeUpdate();
        for (String name : new String[]{"a", "a", "a", "b", "b", "c"}) {
            TestUpdatableEntity row = new TestUpdatableEntity();
            row.setName(name);
            row.setStatus("NEW");
            em.persist(row);
        }
        em.flush();
        em.clear();
    }

    private long rows() {
        return em.createQuery("select count(e) from TestUpdatableEntity e", Long.class).getSingleResult();
    }

    @Test
    @DisplayName("a write that nothing narrows is refused and changes nothing")
    void anUnfilteredWriteIsRefused() {
        SearchCondition<TestUpdatableEntity> everything = new SearchCondition<>();

        assertThatThrownBy(() -> service.deleteWithSearch(everything)).isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> service.updateWithSearch(everything, Map.of("status", "DONE")))
                .isInstanceOf(SearchableValidationException.class);

        assertThat(rows()).isEqualTo(6);
        assertThat(em.createQuery("select count(e) from TestUpdatableEntity e where e.status = 'DONE'", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("a condition or a scope is enough to let a write through")
    void aFilteredWriteRuns() {
        SearchCondition<TestUpdatableEntity> named = new SearchCondition<>();
        named.getNodes().add(new SearchCondition.Condition(null, "name", SearchOperator.EQUALS, "c", null, "name"));
        Specification<TestUpdatableEntity> onlyB = (root, query, cb) -> cb.equal(root.get("name"), "b");

        assertThat(service.deleteWithSearch(named)).isEqualTo(1);
        assertThat(service.deleteWithSearch(new SearchCondition<>(), onlyB)).isEqualTo(2);

        assertThat(rows()).isEqualTo(3);
    }

    @Test
    @DisplayName("a count per value returns no more values than the unit allows, the most frequent first")
    void aCountPerValueIsCapped() {
        assertThat(new SearchAggregator(em).countByValue(TestUpdatableEntity.class, new SearchCondition<>(), null, "name"))
                .extracting(SearchAggregator.ValueCount::value, SearchAggregator.ValueCount::count)
                .containsExactly(tuple("a", 3L), tuple("b", 2L));
    }

    @Test
    @DisplayName("a time strip may have no more slices than the unit allows")
    void aTimeStripIsCapped() {
        TimeBucketCounter counter = new TimeBucketCounter(em);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(counter.count(WallClockEvent.class, new SearchCondition<>(), null, "occurredAt", from, from.plusSeconds(3600), 4))
                .hasSize(4);
        assertThatThrownBy(() -> counter.count(WallClockEvent.class, new SearchCondition<>(), null, "occurredAt",
                from, from.plusSeconds(3600), 5)).isInstanceOf(SearchableValidationException.class).hasMessageContaining("4");
    }
}
