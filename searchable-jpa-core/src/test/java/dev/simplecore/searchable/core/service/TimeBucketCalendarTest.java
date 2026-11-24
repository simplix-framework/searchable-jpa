package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter.TimeBucket;
import dev.simplecore.searchable.core.utils.SearchableTimeZoneHolder;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.WallClockEvent;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Calendar slices and the axis types other than {@code Instant}. Every count is compared with a
 * plain loop over the same moments, and the slice bounds with the calendar of the zone.
 */
@SpringBootTest
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
class TimeBucketCalendarTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired
    private EntityManager em;

    private final List<Instant> moments = new ArrayList<>();
    private ZoneId applicationZone;

    @BeforeEach
    void loadFixture() {
        applicationZone = SearchableTimeZoneHolder.getZoneId();
        SearchableTimeZoneHolder.setZoneId(SEOUL);
        em.createQuery("delete from WallClockEvent").executeUpdate();
        Random random = new Random(20260329L);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 240; i++) {
            // Spread over the first five months of the year, to the second.
            moments.add(start.plusSeconds((long) (random.nextDouble() * 150 * 86_400)));
        }
        // Moments on the edges the cases below cut at.
        moments.add(ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, BERLIN).toInstant());
        moments.add(ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, BERLIN).toInstant());
        moments.add(ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, SEOUL).toInstant());
        for (int i = 0; i < moments.size(); i++) {
            WallClockEvent event = new WallClockEvent();
            event.occurredAt = moments.get(i);
            event.localTime = LocalDateTime.ofInstant(moments.get(i), SEOUL);
            event.offsetTime = moments.get(i).atOffset(ZoneOffset.ofHours(i % 2 == 0 ? 9 : -5));
            event.source = i % 3 == 0 ? "sensor" : "manual";
            em.persist(event);
        }
        em.flush();
        em.clear();
    }

    @AfterEach
    void restoreZone() {
        SearchableTimeZoneHolder.setZoneId(applicationZone);
    }

    private long between(Instant from, Instant to) {
        return moments.stream().filter(moment -> !moment.isBefore(from) && moment.isBefore(to)).count();
    }

    private void assertCountsAgree(List<TimeBucket> slices, Instant from, Instant to) {
        assertThat(slices.get(0).from()).isEqualTo(from);
        assertThat(slices.get(slices.size() - 1).to()).isEqualTo(to);
        for (int i = 0; i < slices.size(); i++) {
            TimeBucket slice = slices.get(i);
            if (i > 0) {
                assertThat(slice.from()).as("slice %d starts where the one before ends", i).isEqualTo(slices.get(i - 1).to());
            }
            assertThat(slice.count()).as("rows in %s", slice).isEqualTo(between(slice.from(), slice.to()));
        }
    }

    @Test
    @DisplayName("days follow the zone's calendar, including the day its clocks change")
    void daysAcrossAClockChange() {
        Instant from = ZonedDateTime.of(2026, 3, 28, 0, 0, 0, 0, BERLIN).toInstant();
        Instant to = ZonedDateTime.of(2026, 3, 31, 0, 0, 0, 0, BERLIN).toInstant();

        List<TimeBucket> days = new TimeBucketCounter(em).countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                "occurredAt", from, to, ChronoUnit.DAYS, BERLIN);

        assertThat(days).extracting(day -> Duration.between(day.from(), day.to()).toHours()).containsExactly(24L, 23L, 24L);
        assertCountsAgree(days, from, to);
    }

    @Test
    @DisplayName("a window that starts and ends inside a unit gets shorter first and last slices")
    void monthsOfAPartialWindow() {
        Instant from = ZonedDateTime.of(2026, 1, 15, 10, 30, 0, 0, SEOUL).toInstant();
        Instant to = ZonedDateTime.of(2026, 4, 10, 0, 0, 0, 0, SEOUL).toInstant();

        List<TimeBucket> months = new TimeBucketCounter(em).countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                "occurredAt", from, to, ChronoUnit.MONTHS, SEOUL);

        assertThat(months).extracting(TimeBucket::from).containsExactly(from,
                ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, SEOUL).toInstant(),
                ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, SEOUL).toInstant(),
                ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, SEOUL).toInstant());
        assertCountsAgree(months, from, to);
    }

    @Test
    @DisplayName("weeks start on Monday and hours on the hour")
    void weeksAndHours() {
        TimeBucketCounter counter = new TimeBucketCounter(em);
        // 2026-02-04 is a Wednesday.
        Instant from = ZonedDateTime.of(2026, 2, 4, 0, 0, 0, 0, SEOUL).toInstant();
        Instant to = ZonedDateTime.of(2026, 2, 20, 0, 0, 0, 0, SEOUL).toInstant();

        List<TimeBucket> weeks = counter.countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                "occurredAt", from, to, ChronoUnit.WEEKS, SEOUL);
        assertThat(weeks).extracting(TimeBucket::from).containsExactly(from,
                ZonedDateTime.of(2026, 2, 9, 0, 0, 0, 0, SEOUL).toInstant(),
                ZonedDateTime.of(2026, 2, 16, 0, 0, 0, 0, SEOUL).toInstant());
        assertCountsAgree(weeks, from, to);

        Instant morning = ZonedDateTime.of(2026, 2, 4, 8, 20, 0, 0, SEOUL).toInstant();
        List<TimeBucket> hours = counter.countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                "occurredAt", morning, morning.plusSeconds(3 * 3600), ChronoUnit.HOURS, SEOUL);
        assertThat(hours).extracting(hour -> Duration.between(hour.from(), hour.to()).toMinutes()).containsExactly(40L, 60L, 60L, 20L);
        assertCountsAgree(hours, morning, morning.plusSeconds(3 * 3600));
    }

    @Test
    @DisplayName("a local date-time axis is read in the application's zone, and an offset axis by its instant")
    void otherAxisTypes() {
        TimeBucketCounter counter = new TimeBucketCounter(em);
        Instant from = ZonedDateTime.of(2026, 2, 1, 0, 0, 0, 0, SEOUL).toInstant();
        Instant to = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, SEOUL).toInstant();

        for (String axis : List.of("localTime", "offsetTime")) {
            assertCountsAgree(counter.countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                    axis, from, to, ChronoUnit.WEEKS, SEOUL), from, to);

            List<Long> sevenths = counter.count(WallClockEvent.class, new SearchCondition<>(), null, axis, from, to, 7);
            long width = Duration.between(from, to).toSeconds() / 7;
            for (int i = 0; i < 7; i++) {
                assertThat(sevenths.get(i)).as("%s, slice %d", axis, i)
                        .isEqualTo(between(from.plusSeconds(width * i), from.plusSeconds(width * (i + 1))));
            }
        }
    }

    @Test
    @DisplayName("the conditions and the scope narrow a strip as they narrow the list")
    void conditionsAndScopeNarrowTheStrip() {
        SearchCondition<WallClockEvent> sensors = new SearchCondition<>();
        sensors.getNodes().add(new SearchCondition.Condition(null, "source", SearchOperator.EQUALS, "sensor", null, "source"));
        Instant cut = Instant.parse("2026-03-01T00:00:00Z");
        Specification<WallClockEvent> early = (root, query, cb) -> cb.lessThan(root.get("occurredAt"), cut);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");

        List<TimeBucket> months = new TimeBucketCounter(em).countByCalendar(WallClockEvent.class, sensors, early,
                "occurredAt", from, to, ChronoUnit.MONTHS, ZoneOffset.UTC);

        long expected = 0;
        for (int i = 0; i < moments.size(); i += 3) {
            if (moments.get(i).isBefore(cut)) {
                expected++;
            }
        }
        assertThat(months.stream().mapToLong(TimeBucket::count).sum()).isEqualTo(expected);
        assertThat(months.subList(2, months.size())).extracting(TimeBucket::count).containsOnly(0L);
    }

    @Test
    @DisplayName("a unit a calendar does not cut by, and a window of too many slices, are refused")
    void invalidRequestsAreRefused() {
        TimeBucketCounter counter = new TimeBucketCounter(em);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> counter.countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                "occurredAt", from, from.plusSeconds(3600), ChronoUnit.MINUTES, SEOUL))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> counter.countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                "occurredAt", from, from.plus(Duration.ofDays(60)), ChronoUnit.HOURS, SEOUL))
                .isInstanceOf(SearchableValidationException.class);
        assertThatThrownBy(() -> counter.countByCalendar(WallClockEvent.class, new SearchCondition<>(), null,
                "source", from, from.plusSeconds(3600), ChronoUnit.HOURS, SEOUL))
                .isInstanceOf(SearchableValidationException.class).hasMessageContaining("source");
    }
}
