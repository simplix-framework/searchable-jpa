package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Aggregate;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.AggregateValue;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Op;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.ValueCount;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.AuditEvent;
import dev.simplecore.searchable.test.entity.AuditLabel;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"searchable.test.h2-url=jdbc:h2:mem:aggregate-audit;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class SearchAggregatorTest {

    @Autowired
    EntityManager em;

    private AuditEvent event(AuditEvent.Level level, Long amount, String... labels) {
        AuditEvent event = new AuditEvent();
        event.level = level;
        event.amount = amount;
        for (String name : labels) {
            AuditLabel label = new AuditLabel();
            label.name = name;
            label.event = event;
            event.labels.add(label);
        }
        em.persist(event);
        return event;
    }

    private SimpleJpaRepository<AuditEvent, Long> repo() {
        return new SimpleJpaRepository<>(AuditEvent.class, em);
    }

    @Test
    void countsPerEnumConstantInDeclarationOrderWithZeroes() {
        event(AuditEvent.Level.HIGH, 5L);
        event(AuditEvent.Level.HIGH, 7L);
        event(AuditEvent.Level.LOW, 1L);
        event(null, 1L);
        em.flush();

        List<ValueCount> counts = new SearchAggregator(em)
                .countByValue(AuditEvent.class, repo(), new SearchCondition<>(), "level");

        assertThat(counts).containsExactly(
                new ValueCount(AuditEvent.Level.LOW, 1),
                new ValueCount(AuditEvent.Level.MID, 0),
                new ValueCount(AuditEvent.Level.HIGH, 2));
    }

    @Test
    void joinedCollectionConditionCountsEachRowOnce() {
        event(AuditEvent.Level.HIGH, 5L, "a", "b");
        event(AuditEvent.Level.HIGH, 3L, "c");
        em.flush();
        SearchCondition<?> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition("labels.name", SearchOperator.IN, List.of("a", "b"), null));

        SearchAggregator aggregator = new SearchAggregator(em);

        assertThat(aggregator.countByValue(AuditEvent.class, repo(), condition, "level"))
                .contains(new ValueCount(AuditEvent.Level.HIGH, 1));
        assertThat(aggregator.aggregate(AuditEvent.class, repo(), condition,
                List.of(new Aggregate("amount", Op.SUM), new Aggregate("id", Op.COUNT))))
                .extracting(AggregateValue::value)
                .map(Number::longValue)
                .containsExactly(5L, 1L);
    }

    @Test
    void aggregatesFollowTheCondition() {
        event(AuditEvent.Level.LOW, 2L);
        event(AuditEvent.Level.HIGH, 10L);
        event(AuditEvent.Level.HIGH, 30L);
        event(AuditEvent.Level.HIGH, 30L);
        em.flush();
        SearchCondition<?> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition("level", SearchOperator.EQUALS, AuditEvent.Level.HIGH, null));

        List<AggregateValue> values = new SearchAggregator(em).aggregate(AuditEvent.class, repo(), condition, List.of(
                new Aggregate("amount", Op.SUM),
                new Aggregate("amount", Op.COUNT_DISTINCT),
                new Aggregate("amount", Op.MIN),
                new Aggregate("amount", Op.MAX)));

        assertThat(values).extracting(AggregateValue::op).containsExactly("sum", "countDistinct", "min", "max");
        assertThat(values).extracting(v -> v.value().longValue()).containsExactly(70L, 2L, 10L, 30L);
    }

    @Test
    void sumOfNoRowsIsZeroAndMaxIsNull() {
        List<AggregateValue> values = new SearchAggregator(em).aggregate(AuditEvent.class, repo(), new SearchCondition<>(),
                List.of(new Aggregate("amount", Op.SUM), new Aggregate("amount", Op.MAX)));

        assertThat(values.get(0).value().longValue()).isZero();
        assertThat(values.get(1).value()).isNull();
    }

    @Test
    void parsesOnlyTheKnownOperations() {
        assertThat(Op.parse("countDistinct")).isEqualTo(Op.COUNT_DISTINCT);
        assertThat(Op.COUNT_DISTINCT.wireName()).isEqualTo("countDistinct");
        for (Op op : Op.values()) {
            assertThat(Op.parse(op.wireName())).isEqualTo(op);
        }
        assertThatThrownBy(() -> Op.parse("median")).isInstanceOf(SearchableValidationException.class)
                .hasMessageContaining("median");
    }

    @Test
    void averagesTheMatchingRows() {
        event(AuditEvent.Level.LOW, 2L);
        event(AuditEvent.Level.HIGH, 10L);
        event(AuditEvent.Level.HIGH, 20L);
        em.flush();
        SearchCondition<?> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition("level", SearchOperator.EQUALS, AuditEvent.Level.HIGH, null));

        List<AggregateValue> values = new SearchAggregator(em).aggregate(AuditEvent.class, repo(), condition,
                List.of(new Aggregate("amount", Op.AVG)));

        assertThat(values.get(0).op()).isEqualTo("avg");
        assertThat(values.get(0).value().doubleValue()).isEqualTo(15.0);
        assertThat(new SearchAggregator(em).aggregate(AuditEvent.class, repo(),
                conditionMatchingNothing(), List.of(new Aggregate("amount", Op.AVG))).get(0).value()).isNull();
    }

    @Test
    void theEarliestAndLatestOfAFieldThatIsNotANumber() {
        Instant early = Instant.parse("2026-01-01T00:00:00Z");
        Instant late = Instant.parse("2026-06-01T12:30:00Z");
        for (Instant occurredAt : List.of(late, early)) {
            AuditEvent event = event(AuditEvent.Level.LOW, 1L);
            event.occurredAt = occurredAt;
        }
        em.flush();

        List<AggregateValue> values = new SearchAggregator(em).aggregate(AuditEvent.class, repo(), new SearchCondition<>(),
                List.of(new Aggregate("occurredAt", Op.MIN), new Aggregate("occurredAt", Op.MAX),
                        new Aggregate("level", Op.MAX), new Aggregate("amount", Op.MAX)));

        assertThat(values.get(0).result()).isEqualTo(early);
        assertThat(values.get(1).result()).isEqualTo(late);
        assertThat(values.get(0).value()).as("a date is not a number").isNull();
        assertThat(values.get(2).result()).isEqualTo(AuditEvent.Level.LOW);
        assertThat(values.get(3).value().longValue()).isEqualTo(1L);
        assertThat(values.get(3).result()).isEqualTo(values.get(3).value());
    }

    @Test
    void aFieldOrOperationThatCannotBeAggregatedIsRefusedByName() {
        SearchAggregator aggregator = new SearchAggregator(em);
        SearchCondition<?> all = new SearchCondition<>();

        assertThatThrownBy(() -> aggregator.aggregate(AuditEvent.class, repo(), all, List.of(new Aggregate("nope", Op.COUNT))))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("nope");
        assertThatThrownBy(() -> aggregator.aggregate(AuditEvent.class, repo(), all, List.of(new Aggregate("labels", Op.COUNT))))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("labels");
        assertThatThrownBy(() -> aggregator.aggregate(AuditEvent.class, repo(), all, List.of(new Aggregate("level", Op.SUM))))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("level");
        assertThatThrownBy(() -> aggregator.aggregate(AuditEvent.class, repo(), all, List.of(new Aggregate("occurredAt", Op.AVG))))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("occurredAt");
        assertThatThrownBy(() -> aggregator.countByValue(AuditEvent.class, repo(), all, "nope"))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("nope");
        assertThatThrownBy(() -> aggregator.countByValue(AuditEvent.class, repo(), all, "labels"))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("labels");
    }

    private SearchCondition<?> conditionMatchingNothing() {
        SearchCondition<?> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition("amount", SearchOperator.EQUALS, -1L, null));
        return condition;
    }
}
