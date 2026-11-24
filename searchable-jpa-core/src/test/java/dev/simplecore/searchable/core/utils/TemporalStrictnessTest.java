package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A search value is a date the calendar has, in every accepted format, and a day without a time
 * closes a range at the end of that day in every accepted format.
 */
class TemporalStrictnessTest {

    private static final List<String> DAY_FORMATS_OF_31_JAN_2024 =
            List.of("2024-01-31", "2024/01/31", "31-01-2024", "31/01/2024", "20240131", "2024.01.31");

    @Test
    @DisplayName("a date the calendar does not have is refused, not moved to the nearest valid day")
    void nonexistentDatesAreRefused() {
        for (String value : List.of("2024-02-31", "2023-02-29", "2024-04-31", "2024/02/31", "31-02-2024",
                "31/04/2024", "20240231", "2024.02.31", "2024-02-31 10:00:00", "2024-02-31T10:00:00")) {
            for (Class<?> type : List.of(LocalDate.class, LocalDateTime.class, Instant.class, OffsetDateTime.class)) {
                if (type == LocalDate.class && value.length() > 10) {
                    continue;
                }
                assertThatThrownBy(() -> SearchableValueParser.parseValue(value, type))
                        .as("%s as %s", value, type.getSimpleName())
                        .isInstanceOf(SearchableParseException.class);
            }
        }
    }

    @Test
    @DisplayName("the leap day and month ends that exist are still read")
    void existingBoundaryDatesAreRead() {
        assertThat(SearchableValueParser.parseValue("2024-02-29", LocalDate.class)).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(SearchableValueParser.parseValue("29-02-2024", LocalDate.class)).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(SearchableValueParser.parseValue("2024/04/30", LocalDate.class)).isEqualTo(LocalDate.of(2024, 4, 30));
        assertThat(SearchableValueParser.parseValue("20241231", LocalDate.class)).isEqualTo(LocalDate.of(2024, 12, 31));
    }

    @Test
    @DisplayName("every accepted day format closes a range at the end of that day")
    void everyDayFormatClosesARangeAtTheEndOfTheDay() {
        LocalDateTime startOfDay = LocalDate.of(2024, 1, 31).atStartOfDay();
        LocalDateTime endOfDay = LocalDate.of(2024, 1, 31).atTime(LocalTime.MAX);
        for (String value : DAY_FORMATS_OF_31_JAN_2024) {
            assertThat(SearchableValueParser.parseValueForBetween(value, LocalDateTime.class, false))
                    .as("start of %s", value).isEqualTo(startOfDay);
            assertThat(SearchableValueParser.parseValueForBetween(value, LocalDateTime.class, true))
                    .as("end of %s", value).isEqualTo(endOfDay);
        }
    }

    @Test
    @DisplayName("a value with a time of day is not widened")
    void aValueWithATimeIsNotWidened() {
        LocalDateTime exact = LocalDateTime.of(2024, 1, 31, 15, 30, 45);
        assertThat(SearchableValueParser.parseValueForBetween("2024-01-31T15:30:45", LocalDateTime.class, true)).isEqualTo(exact);
        assertThat(SearchableValueParser.parseValueForBetween("31-01-2024 15:30:45", LocalDateTime.class, true)).isEqualTo(exact);
    }
}
