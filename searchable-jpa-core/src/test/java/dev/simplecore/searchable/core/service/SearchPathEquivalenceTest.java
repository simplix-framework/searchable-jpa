package dev.simplecore.searchable.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.utils.SearchableTimeZoneHolder;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.AuditEvent;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.BETWEEN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.GREATER_THAN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.IN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.NOT_BETWEEN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * One request reaches the query by three paths: query parameters, a JSON body and the builder.
 * The same text has to select the same rows on all three, so each case here is asked three times
 * and the answers compared with each other as well as with the expected rows.
 */
@SpringBootTest(properties = {"searchable.test.h2-url=jdbc:h2:mem:path-equivalence;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class SearchPathEquivalenceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static class EventSearchDto {
        @SearchableField(operators = {BETWEEN, NOT_BETWEEN, GREATER_THAN}, sortable = true)
        private Instant occurredAt;
        @SearchableField(operators = {EQUALS, IN})
        private AuditEvent.Level level;
        @SearchableField(operators = {EQUALS, BETWEEN, GREATER_THAN}, sortable = true)
        private Long amount;

        public Instant getOccurredAt() { return occurredAt; }
        public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
        public AuditEvent.Level getLevel() { return level; }
        public void setLevel(AuditEvent.Level level) { this.level = level; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
    }

    @Autowired
    private EntityManager em;

    private ZoneId previousZone;
    private SearchableServiceDelegate<AuditEvent, Long> service;

    @BeforeEach
    void setUp() {
        previousZone = SearchableTimeZoneHolder.getZoneId();
        SearchableTimeZoneHolder.setZoneId(SEOUL);
        service = new SearchableServiceDelegate<>(new SimpleJpaRepository<>(AuditEvent.class, em), em, AuditEvent.class);
        event("2026-03-10T00:00:00", AuditEvent.Level.LOW, 1L);
        event("2026-03-10T12:00:00", AuditEvent.Level.MID, 2L);
        event("2026-03-10T23:59:59.999", AuditEvent.Level.HIGH, 3L);
        event("2026-03-11T00:00:00", AuditEvent.Level.HIGH, 4L);
        em.flush();
        em.clear();
    }

    @AfterEach
    void restoreZone() {
        SearchableTimeZoneHolder.setZoneId(previousZone);
    }

    private void event(String localTime, AuditEvent.Level level, Long amount) {
        AuditEvent event = new AuditEvent();
        event.occurredAt = LocalDateTime.parse(localTime).atZone(SEOUL).toInstant();
        event.level = level;
        event.amount = amount;
        em.persist(event);
    }

    private List<Long> amounts(SearchCondition<?> condition) {
        return service.findAllWithSearch(condition).getContent().stream().map(e -> e.amount).sorted().toList();
    }

    /** Asks one question by query parameters, by JSON and by the builder, and expects one answer. */
    private void sameOnEveryPath(String parameter, String value, String jsonCondition,
                                 Consumer<FirstCondition> built, Long... expectedAmounts) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(parameter, value);
        SearchCondition<EventSearchDto> fromParams = new SearchableParamsParser<>(EventSearchDto.class).convert(params);
        SearchCondition<EventSearchDto> fromJson;
        try {
            fromJson = SearchCondition.fromJson("{\"conditions\":[" + jsonCondition.replace('\'', '"') + "]}",
                    EventSearchDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        SearchCondition<EventSearchDto> fromBuilder = SearchConditionBuilder.create(EventSearchDto.class)
                .where(group -> built.accept(group)).build();

        assertThat(amounts(fromParams)).as("query parameter %s=%s", parameter, value).containsExactly(expectedAmounts);
        assertThat(amounts(fromJson)).as("JSON %s", jsonCondition).containsExactly(expectedAmounts);
        assertThat(amounts(fromBuilder)).as("builder for %s", parameter).containsExactly(expectedAmounts);
    }

    @Test
    @DisplayName("a day given without a time covers that whole day and stops before the next midnight")
    void aDayWithoutATimeCoversTheWholeDay() {
        sameOnEveryPath("occurredAt.between", "2026-03-10,2026-03-10",
                "{'field':'occurredAt','searchOperator':'between','value':'2026-03-10','value2':'2026-03-10'}",
                w -> w.between("occurredAt", "2026-03-10", "2026-03-10"),
                1L, 2L, 3L);
    }

    @Test
    @DisplayName("the complement of a day excludes the same rows on every path")
    void theComplementOfADay() {
        sameOnEveryPath("occurredAt.notBetween", "2026-03-10,2026-03-10",
                "{'field':'occurredAt','searchOperator':'notBetween','value':'2026-03-10','value2':'2026-03-10'}",
                w -> w.notBetween("occurredAt", "2026-03-10", "2026-03-10"),
                4L);
    }

    @Test
    @DisplayName("a range with explicit times is inclusive at both ends")
    void aRangeWithTimesIsInclusive() {
        sameOnEveryPath("occurredAt.between", "2026-03-10T12:00:00,2026-03-11T00:00:00",
                "{'field':'occurredAt','searchOperator':'between','value':'2026-03-10T12:00:00','value2':'2026-03-11T00:00:00'}",
                w -> w.between("occurredAt", "2026-03-10T12:00:00", "2026-03-11T00:00:00"),
                2L, 3L, 4L);
    }

    @Test
    @DisplayName("numbers, enums and comparisons read the same text the same way")
    void otherTypesAgree() {
        sameOnEveryPath("amount.between", "2,3",
                "{'field':'amount','searchOperator':'between','value':2,'value2':3}",
                w -> w.between("amount", "2", "3"),
                2L, 3L);
        sameOnEveryPath("level.in", "LOW,HIGH",
                "{'field':'level','searchOperator':'in','value':['LOW','HIGH']}",
                w -> w.in("level", List.of("LOW", "HIGH")),
                1L, 3L, 4L);
        sameOnEveryPath("occurredAt.greaterThan", "2026-03-10T12:00:00",
                "{'field':'occurredAt','searchOperator':'greaterThan','value':'2026-03-10T12:00:00'}",
                w -> w.greaterThan("occurredAt", "2026-03-10T12:00:00"),
                3L, 4L);
    }
}
