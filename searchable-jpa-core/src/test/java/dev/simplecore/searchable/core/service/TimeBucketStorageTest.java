package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.AuditEvent;
import dev.simplecore.searchable.test.entity.WallClockEvent;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An instant kept in a column without an offset, written in a zone that is not UTC. Read as a
 * number such a column is off by the zone's offset, so the slices must still come out where the
 * rows are. The zone is set on the connection, not on the machine, so the case is the same wherever
 * the test runs.
 */
@SpringBootTest(properties = {"searchable.test.h2-url=jdbc:h2:mem:bucket-storage;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Seoul",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class TimeBucketStorageTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private EntityManager em;

    private SimpleJpaRepository<AuditEvent, Long> repo() {
        return new SimpleJpaRepository<>(AuditEvent.class, em);
    }

    private int jdbcTypeOfTheWallClockColumn() {
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("select occurred_at from wall_clock_event where 1 = 0");
                 ResultSet rows = statement.executeQuery()) {
                return rows.getMetaData().getColumnType(1);
            }
        });
    }

    private static List<String> occupied(List<Long> counts) {
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            if (counts.get(i) != 0) {
                slots.add(i + "=" + counts.get(i));
            }
        }
        return slots;
    }

    @Test
    @DisplayName("rows written as local wall-clock time land in the slice of their own instant")
    void zonelessColumnWrittenOutsideUtcIsSlicedByInstant() {
        Instant from = LocalDate.of(2026, 11, 1).atStartOfDay(SEOUL).toInstant();
        Instant to = LocalDate.of(2026, 11, 2).atStartOfDay(SEOUL).toInstant();
        em.createQuery("delete from WallClockEvent").executeUpdate();
        for (String local : List.of("00:30:00", "13:30:00", "23:59:59")) {
            WallClockEvent event = new WallClockEvent();
            event.occurredAt = LocalDate.of(2026, 11, 1).atTime(LocalTime.parse(local)).atZone(SEOUL).toInstant();
            em.persist(event);
        }
        em.flush();
        assertThat(jdbcTypeOfTheWallClockColumn())
                .as("the column this test is about keeps no offset").isEqualTo(Types.TIMESTAMP);

        TimeBucketCounter counter = new TimeBucketCounter(em);
        var events = new SimpleJpaRepository<WallClockEvent, Long>(WallClockEvent.class, em);

        // 24 divides the window evenly, which is the shape the arithmetic form would be chosen for.
        assertThat(occupied(counter.count(WallClockEvent.class, events, new SearchCondition<>(), "occurredAt", from, to, 24)))
                .containsExactly("0=1", "13=1", "23=1");
        assertThat(occupied(counter.count(WallClockEvent.class, events, new SearchCondition<>(), "occurredAt", from, to, 7)))
                .containsExactly("0=1", "3=1", "6=1");
    }

    @Test
    @DisplayName("a window or a time axis that cannot be counted is refused as invalid input")
    void invalidInputIsRefused() {
        TimeBucketCounter counter = new TimeBucketCounter(em);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        SearchCondition<?> all = new SearchCondition<>();

        assertThatThrownBy(() -> counter.count(AuditEvent.class, repo(), all, "occurredAt", start, start, 4))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> counter.count(AuditEvent.class, repo(), all, "occurredAt", start, start.plusSeconds(60), 0))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> counter.count(AuditEvent.class, repo(), all, "occurredAt", start, start.plusSeconds(60),
                TimeBucketCounter.MAX_BUCKETS + 1)).isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> counter.count(AuditEvent.class, repo(), all, "nope", start, start.plusSeconds(60), 4))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("nope");
        assertThatThrownBy(() -> counter.count(AuditEvent.class, repo(), all, "amount", start, start.plusSeconds(60), 4))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("amount");
    }
}
