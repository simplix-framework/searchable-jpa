package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.ScrollPosition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {

    private static final Map<String, Object> VALUES = new LinkedHashMap<>();

    static {
        VALUES.put("id", 9_007_199_254_740_993L);
        VALUES.put("rank", 7);
        VALUES.put("small", (short) 3);
        VALUES.put("ratio", 0.1d);
        VALUES.put("price", new BigDecimal("1234567890.123456789000"));
        VALUES.put("active", Boolean.FALSE);
        VALUES.put("status", TestPostStatus.ARCHIVED);
        VALUES.put("token", UUID.fromString("0b8f2f06-5f0b-4c1e-9d55-1b9a6d1c7e11"));
        VALUES.put("day", LocalDate.of(2026, 2, 28));
        VALUES.put("minute", LocalDateTime.of(2026, 2, 28, 9, 5));
        VALUES.put("nanos", LocalDateTime.of(2026, 2, 28, 23, 59, 59, 123_456_789));
        VALUES.put("clock", LocalTime.of(7, 30, 15));
        VALUES.put("instant", Instant.parse("2026-02-28T14:59:59.123456Z"));
        VALUES.put("offset", OffsetDateTime.parse("2026-02-28T23:59:59.5+09:00"));
        VALUES.put("legacy", new Date(1_772_000_000_123L));
        VALUES.put("plain", "plain text");
        VALUES.put("author.name", "a&b=c d/e+f%");
        VALUES.put("wordNull", "null");
        VALUES.put("composed", "\u304c \u30ab\u3099 \uff21");
        VALUES.put("empty", "");
        VALUES.put("missing", null);
    }

    private static Class<?> typeOf(String key) {
        Object value = VALUES.get(key);
        return value == null ? String.class : value.getClass();
    }

    @Test
    @DisplayName("a keyset position reads back as the position that was written, for every kind of sort value")
    void keysetPositionsRoundTrip() {
        for (ScrollPosition position : new ScrollPosition[]{ScrollPosition.forward(VALUES), ScrollPosition.backward(VALUES)}) {
            String cursor = SearchCursor.encode(position);

            assertThat(cursor).matches("[A-Za-z0-9_-]+");
            assertThat(SearchCursor.decode(cursor, SearchCursorTest::typeOf)).isEqualTo(position);
        }
    }

    @Test
    @DisplayName("an offset position and the first position read back")
    void offsetAndInitialPositionsRoundTrip() {
        assertThat(SearchCursor.decode(SearchCursor.encode(ScrollPosition.offset(41)), SearchCursorTest::typeOf))
                .isEqualTo(ScrollPosition.offset(41));
        assertThat(SearchCursor.decode(SearchCursor.encode(ScrollPosition.offset()), SearchCursorTest::typeOf))
                .isEqualTo(ScrollPosition.offset());
        assertThat(SearchCursor.decode(SearchCursor.encode(ScrollPosition.keyset()), SearchCursorTest::typeOf))
                .isEqualTo(ScrollPosition.keyset());
        assertThat(SearchCursor.decode(null, SearchCursorTest::typeOf)).isEqualTo(ScrollPosition.keyset());
        assertThat(SearchCursor.decode("  ", SearchCursorTest::typeOf)).isEqualTo(ScrollPosition.keyset());
    }

    @Test
    @DisplayName("text that is not a cursor is refused as invalid input")
    void garbageIsRefused() {
        // Not Base64, an unknown kind, a negative offset, an offset that is no number, a value of the wrong type.
        for (String garbage : new String[]{"***", "QQ", "Ty0x", "T3h5eg", "RiZpZD1hYmM"}) {
            assertThatThrownBy(() -> SearchCursor.decode(garbage, key -> Long.class))
                    .as(garbage).isInstanceOf(SearchableValidationException.class);
        }
    }
}
