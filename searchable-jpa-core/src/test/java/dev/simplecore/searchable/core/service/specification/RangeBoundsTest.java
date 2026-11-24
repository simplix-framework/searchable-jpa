package dev.simplecore.searchable.core.service.specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangeBoundsTest {

    private static final LocalDateTime END_OF_DAY = LocalDate.of(2026, 3, 10).atTime(LocalTime.MAX);
    private static final LocalDateTime NEXT_MIDNIGHT = LocalDate.of(2026, 3, 11).atStartOfDay();

    @Test
    @DisplayName("the last nanosecond of a second becomes an exclusive bound one nanosecond later")
    void endOfUnitBecomesExclusive() {
        ZoneId seoul = ZoneId.of("Asia/Seoul");

        assertThat(RangeBounds.exclusiveUpperBound(END_OF_DAY)).isEqualTo(NEXT_MIDNIGHT);
        assertThat(RangeBounds.exclusiveUpperBound(END_OF_DAY.atZone(seoul).toInstant()))
                .isEqualTo(NEXT_MIDNIGHT.atZone(seoul).toInstant());
        assertThat(RangeBounds.exclusiveUpperBound(END_OF_DAY.atOffset(ZoneOffset.UTC)))
                .isEqualTo(NEXT_MIDNIGHT.atOffset(ZoneOffset.UTC));
        assertThat(RangeBounds.exclusiveUpperBound(END_OF_DAY.atZone(seoul)))
                .isEqualTo(NEXT_MIDNIGHT.atZone(seoul));
        Date lastMillisecond = Date.from(END_OF_DAY.atZone(seoul).toInstant());
        assertThat(RangeBounds.exclusiveUpperBound(lastMillisecond))
                .isEqualTo(Date.from(NEXT_MIDNIGHT.atZone(seoul).toInstant()));
    }

    @Test
    @DisplayName("any other bound stays inclusive as given")
    void otherBoundsStayInclusive() {
        assertThat(RangeBounds.exclusiveUpperBound(LocalDateTime.of(2026, 3, 10, 15, 30, 45))).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(LocalDateTime.of(2026, 3, 10, 23, 59, 59, 999_000_000))).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(Instant.parse("2026-03-10T15:30:45Z"))).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(Date.from(Instant.parse("2026-03-10T15:30:45.500Z")))).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(100L)).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(LocalDate.of(2026, 3, 10))).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(null)).isNull();
    }

    @Test
    @DisplayName("the largest representable values have no later instant and stay inclusive")
    void maximumValuesStayInclusive() {
        assertThat(RangeBounds.exclusiveUpperBound(LocalDateTime.MAX)).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(Instant.MAX)).isNull();
        assertThat(RangeBounds.exclusiveUpperBound(OffsetDateTime.MAX)).isNull();
    }
}
