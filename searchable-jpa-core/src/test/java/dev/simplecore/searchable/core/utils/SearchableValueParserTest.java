package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

/**
 * Parsing of text into numbers, booleans, characters, enums and the edge cases around empty input.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SearchableValueParserTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        // Set English locale for tests to ensure consistent error messages
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        // This test context's application zone is the JVM zone (deployment rule 4),
        // so timezone-less values resolve exactly as these assertions expect.
        SearchableTimeZoneHolder.setZoneId(ZoneId.systemDefault());
    }

    @Test
    @DisplayName("String values should be parsed correctly")
    void testStringParsing() {
        // Given & When & Then
        assertThat(SearchableValueParser.parseValue("hello", String.class)).isEqualTo("hello");
        assertThat(SearchableValueParser.parseValue("", String.class)).isNull(); // Empty string returns null
        assertThat(SearchableValueParser.parseValue("   ", String.class)).isNull(); // Whitespace returns null
        assertThat(SearchableValueParser.parseValue("null", String.class)).isNull();
        assertThat(SearchableValueParser.parseValue(null, String.class)).isNull();
    }

    @Test
    @DisplayName("Boolean values should be parsed correctly")
    void testBooleanParsing() {
        // True values
        assertThat((Boolean) SearchableValueParser.parseValue("true", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("TRUE", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("1", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("yes", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("Y", Boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("on", Boolean.class)).isTrue();

        // False values
        assertThat((Boolean) SearchableValueParser.parseValue("false", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("FALSE", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("0", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("no", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("N", Boolean.class)).isFalse();
        assertThat((Boolean) SearchableValueParser.parseValue("off", Boolean.class)).isFalse();

        // Primitive boolean
        assertThat((Boolean) SearchableValueParser.parseValue("true", boolean.class)).isTrue();
        assertThat((Boolean) SearchableValueParser.parseValue("false", boolean.class)).isFalse();

        // Invalid boolean
        assertThatThrownBy(() -> SearchableValueParser.parseValue("invalid", Boolean.class))
            .isInstanceOf(SearchableParseException.class)
            .hasMessageContaining("Invalid boolean value");
    }

    @Test
    @DisplayName("Character values should be parsed correctly")
    void testCharacterParsing() {
        // Valid character
        assertThat(SearchableValueParser.parseValue("A", Character.class)).isEqualTo('A');
        assertThat(SearchableValueParser.parseValue("1", char.class)).isEqualTo('1');

        // Invalid character length
        assertThatThrownBy(() -> SearchableValueParser.parseValue("AB", Character.class))
            .isInstanceOf(SearchableParseException.class)
            .hasMessageContaining("exactly one character long");

        // Empty string returns null, so won't reach character parsing
        assertThat(SearchableValueParser.parseValue("", Character.class)).isNull();
    }

    @Test
    @DisplayName("Integer values should be parsed correctly")
    void testIntegerParsing() {
        // Valid integers
        assertThat(SearchableValueParser.parseValue("123", Integer.class)).isEqualTo(123);
        assertThat(SearchableValueParser.parseValue("-456", int.class)).isEqualTo(-456);
        assertThat(SearchableValueParser.parseValue("0", Integer.class)).isEqualTo(0);

        // With grouping separators
        assertThat(SearchableValueParser.parseValue("1,000", Integer.class)).isEqualTo(1000);
        assertThat(SearchableValueParser.parseValue("1 000", Integer.class)).isEqualTo(1000);

        // Invalid integers
        assertThatThrownBy(() -> SearchableValueParser.parseValue("abc", Integer.class))
            .isInstanceOf(SearchableParseException.class);

        assertThatThrownBy(() -> SearchableValueParser.parseValue("123.45", Integer.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("Long values should be parsed correctly")
    void testLongParsing() {
        assertThat(SearchableValueParser.parseValue("9223372036854775807", Long.class))
            .isEqualTo(Long.MAX_VALUE);
        assertThat(SearchableValueParser.parseValue("-9223372036854775808", long.class))
            .isEqualTo(Long.MIN_VALUE);
    }

    @Test
    @DisplayName("Float and Double values should be parsed correctly")
    void testFloatingPointParsing() {
        // Float
        assertThat(SearchableValueParser.parseValue("123.45", Float.class)).isEqualTo(123.45f);
        assertThat(SearchableValueParser.parseValue("-67.89", float.class)).isEqualTo(-67.89f);

        // Double
        assertThat(SearchableValueParser.parseValue("123.456789", Double.class)).isEqualTo(123.456789);
        assertThat(SearchableValueParser.parseValue("-987.654321", double.class)).isEqualTo(-987.654321);

        // Scientific notation
        assertThat(SearchableValueParser.parseValue("1.23E+10", Double.class)).isEqualTo(1.23E+10);
    }

    @Test
    @DisplayName("BigDecimal and BigInteger should be parsed correctly")
    void testBigNumberParsing() {
        // BigDecimal
        BigDecimal expectedDecimal = new BigDecimal("123456789.987654321");
        assertThat(SearchableValueParser.parseValue("123456789.987654321", BigDecimal.class))
            .isEqualTo(expectedDecimal);

        // BigInteger
        BigInteger expectedInteger = new BigInteger("12345678901234567890");
        assertThat(SearchableValueParser.parseValue("12345678901234567890", BigInteger.class))
            .isEqualTo(expectedInteger);
    }

    @Test
    @DisplayName("Enum values should be parsed correctly")
    void testEnumParsing() {
        // Exact match
        assertThat(SearchableValueParser.parseValue("PUBLISHED", TestPostStatus.class))
            .isEqualTo(TestPostStatus.PUBLISHED);

        // Case insensitive
        assertThat(SearchableValueParser.parseValue("draft", TestPostStatus.class))
            .isEqualTo(TestPostStatus.DRAFT);
        assertThat(SearchableValueParser.parseValue("ARCHIVED", TestPostStatus.class))
            .isEqualTo(TestPostStatus.ARCHIVED);

        // Invalid enum value
        assertThatThrownBy(() -> SearchableValueParser.parseValue("INVALID", TestPostStatus.class))
            .isInstanceOf(SearchableParseException.class)
            .hasMessageContaining("Invalid enum value");
    }

    @Test
    @DisplayName("Null and empty values should be handled correctly")
    void testNullAndEmptyValues() {
        // Null input
        assertThat(SearchableValueParser.parseValue(null, String.class)).isNull();
        assertThat(SearchableValueParser.parseValue(null, Integer.class)).isNull();

        // Empty string
        assertThat(SearchableValueParser.parseValue("", String.class)).isNull();
        assertThat(SearchableValueParser.parseValue("   ", String.class)).isNull();

        // "null" string
        assertThat(SearchableValueParser.parseValue("null", String.class)).isNull();
        assertThat(SearchableValueParser.parseValue("NULL", Integer.class)).isNull();
    }

    @Test
    @DisplayName("Unicode and special characters should be handled correctly")
    void testUnicodeAndSpecialCharacters() {
        // Unicode normalization
        assertThat(SearchableValueParser.parseValue("café", String.class)).isEqualTo("café");

        // BOM removal
        String withBom = "\uFEFF" + "test";
        assertThat(SearchableValueParser.parseValue(withBom, String.class)).isEqualTo("test");

        // Special characters in numbers
        assertThat(SearchableValueParser.parseValue("1,234", Integer.class)).isEqualTo(1234);
        assertThat(SearchableValueParser.parseValue("1 234", Integer.class)).isEqualTo(1234);
    }

    @Test
    @DisplayName("Edge cases and error conditions should be handled correctly")
    void testEdgeCasesAndErrors() {
        // Unsupported type - Object.class returns as String
        assertThat(SearchableValueParser.parseValue("test", Object.class)).isEqualTo("test");

        // Number overflow
        assertThatThrownBy(() -> SearchableValueParser.parseValue("999999999999999999999", Integer.class))
            .isInstanceOf(SearchableParseException.class);

        // Invalid date
        assertThatThrownBy(() -> SearchableValueParser.parseValue("2023-13-32", LocalDate.class))
            .isInstanceOf(SearchableParseException.class);

        // Invalid time
        assertThatThrownBy(() -> SearchableValueParser.parseValue("25:99:99", LocalTime.class))
            .isInstanceOf(SearchableParseException.class);
    }

    @Test
    @DisplayName("Byte and Short values should be parsed correctly")
    void testByteAndShortParsing() {
        // Byte
        assertThat(SearchableValueParser.parseValue("127", Byte.class)).isEqualTo((byte) 127);
        assertThat(SearchableValueParser.parseValue("-128", byte.class)).isEqualTo((byte) -128);

        // Short
        assertThat(SearchableValueParser.parseValue("32767", Short.class)).isEqualTo((short) 32767);
        assertThat(SearchableValueParser.parseValue("-32768", short.class)).isEqualTo((short) -32768);

        // Overflow
        assertThatThrownBy(() -> SearchableValueParser.parseValue("128", Byte.class))
            .isInstanceOf(SearchableParseException.class);
        assertThatThrownBy(() -> SearchableValueParser.parseValue("32768", Short.class))
            .isInstanceOf(SearchableParseException.class);
    }
}
