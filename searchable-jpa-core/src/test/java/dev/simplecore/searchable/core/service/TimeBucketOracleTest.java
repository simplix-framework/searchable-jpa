package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.AuditEvent;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The counter has two forms, an arithmetic group-by and one conditional sum per slice. Both have to
 * give the answer a plain loop over the same instants gives, so each window here is counted by the
 * database and by that loop, for bucket counts that divide the window and for ones that do not.
 */
@SpringBootTest(properties = {"searchable.test.h2-url=jdbc:h2:mem:bucket-oracle;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class TimeBucketOracleTest {

    @Autowired
    private EntityManager em;

    @BeforeEach
    void startFromNoEvents() {
        em.createQuery("delete from AuditLabel").executeUpdate();
        em.createQuery("delete from AuditEvent").executeUpdate();
    }

    /** The slice a half-open window divided into equal parts puts an instant in, or -1 outside it. */
    private static int slot(Instant instant, Instant from, Instant to, int buckets) {
        long span = to.toEpochMilli() - from.toEpochMilli();
        long elapsed = instant.toEpochMilli() - from.toEpochMilli();
        if (elapsed < 0 || elapsed >= span) {
            return -1;
        }
        return (int) Math.floorDiv(elapsed * buckets, span);
    }

    @Test
    @DisplayName("both forms agree with a plain loop over the same instants")
    void bothFormsAgreeWithAPlainLoop() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Random random = new Random(20260501L);
        List<Instant> instants = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            // Spread over three days around the window, so rows fall before, inside and after it.
            instants.add(from.minusSeconds(86_400).plusMillis((long) (random.nextDouble() * 3 * 86_400_000L)));
        }
        // Rows exactly on the window's edges and on an inner slice edge.
        instants.add(from);
        instants.add(from.plusSeconds(86_400));
        instants.add(from.plusSeconds(3_600));
        for (Instant instant : instants) {
            AuditEvent event = new AuditEvent();
            event.occurredAt = instant;
            em.persist(event);
        }
        em.flush();
        var repo = new SimpleJpaRepository<AuditEvent, Long>(AuditEvent.class, em);
        TimeBucketCounter counter = new TimeBucketCounter(em);

        Instant to = from.plusSeconds(86_400);
        // 24, 48 and 96 divide a day of milliseconds; 7, 11 and 13 do not.
        for (int buckets : new int[]{1, 24, 48, 96, 7, 11, 13}) {
            long[] expected = new long[buckets];
            for (Instant instant : instants) {
                int slot = slot(instant, from, to, buckets);
                if (slot >= 0) {
                    expected[slot]++;
                }
            }
            List<Long> counted = counter.count(AuditEvent.class, repo, new SearchCondition<>(), "occurredAt", from, to, buckets);
            assertThat(counted).as("%d buckets", buckets).hasSize(buckets);
            for (int i = 0; i < buckets; i++) {
                assertThat(counted.get(i)).as("slice %d of %d", i, buckets).isEqualTo(expected[i]);
            }
        }
    }

    @Test
    @DisplayName("a row stored with an offset other than UTC is sliced by the instant it names")
    void aRowStoredWithAnotherOffsetIsSlicedByItsInstant() {
        // 2026-11-01T00:30+09:00 is 2026-10-31T15:30Z: half an hour into the window below. The row
        // is written through JDBC so that the offset reaches the column as given.
        OffsetDateTime written = OffsetDateTime.parse("2026-11-01T00:30:00+09:00");
        OffsetDateTime stored = em.unwrap(Session.class).doReturningWork(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("insert into audit_event (id, occurred_at, amount) "
                    + "values (next value for audit_event_seq, ?, 1)")) {
                insert.setObject(1, written);
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement("select occurred_at from audit_event");
                 ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getObject(1, OffsetDateTime.class);
            }
        });
        assertThat(stored.getOffset()).as("the row keeps the offset it was written with").isEqualTo(written.getOffset());
        Instant from = Instant.parse("2026-10-31T15:00:00Z");
        var repo = new SimpleJpaRepository<AuditEvent, Long>(AuditEvent.class, em);

        List<Long> hours = new TimeBucketCounter(em).count(AuditEvent.class, repo, new SearchCondition<>(),
                "occurredAt", from, from.plusSeconds(86_400), 24);

        assertThat(hours.get(0)).isEqualTo(1L);
        assertThat(hours.stream().mapToLong(Long::longValue).sum()).isEqualTo(1L);
    }
}
