package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;

/**
 * Utility class for parsing values to specific types.
 * Supports various data types including primitives, temporal types, and enums; any other type is
 * read through the {@link SearchableConversionHolder conversion service}.
 */
public class SearchableValueParser {
    private static final Map<Class<?>, Map<String, Object>> ENUM_CACHE = new ConcurrentHashMap<>();

    /**
     * Parse a string value to the specified target type for between operations.
     * For date types, if only date is provided (no time), it will be adjusted for range queries:
     * - Start value: set to beginning of day (00:00:00)
     * - End value: set to end of day (23:59:59.999999999)
     *
     * @param value      the string value to parse
     * @param targetType the target type to parse to
     * @param isEndValue true if this is the end value of a between operation
     * @return the parsed value
     * @throws SearchableParseException if parsing fails
     */
    public static Object parseValueForBetween(String value, Class<?> targetType, boolean isEndValue) {
        return parse(value, targetType, isEndValue);
    }

    /**
     * Parse a string value to the specified target type.
     *
     * @param value      the string value to parse
     * @param targetType the target type to parse to
     * @return the parsed value
     * @throws SearchableParseException if parsing fails or the type cannot be read from text
     */
    public static Object parseValue(String value, Class<?> targetType) {
        return parse(value, targetType, null);
    }

    private static Object parse(String raw, Class<?> targetType, Boolean rangeEnd) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String value = normalizeValue(raw);

        if (targetType.isEnum()) {
            return parseEnum(value, targetType);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return parseBoolean(value);
        }
        if (TemporalValueParser.isTemporal(targetType)) {
            return TemporalValueParser.parse(value, targetType, rangeEnd);
        }
        if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive() && targetType != char.class) {
            return parseNumericValue(value, targetType);
        }
        if (targetType == Character.class || targetType == char.class) {
            return parseCharacter(value);
        }
        if (targetType == UUID.class) {
            return parseUuid(value);
        }
        // Text is already a value of the type (String, Object, CharSequence, Comparable).
        if (targetType.isInstance(value)) {
            return value;
        }
        // A map, collection or array attribute is stored as text and matched as text.
        if (Map.class.isAssignableFrom(targetType) || Collection.class.isAssignableFrom(targetType)
                || targetType.isArray()) {
            return value;
        }
        return convert(value, targetType);
    }

    private static Object convert(String value, Class<?> targetType) {
        ConversionService conversionService = SearchableConversionHolder.getConversionService();
        if (!conversionService.canConvert(String.class, targetType)) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.type.unsupported", new Object[]{targetType.getSimpleName()}));
        }
        try {
            return conversionService.convert(value, targetType);
        } catch (ConversionException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.value.parse.failed",
                            new Object[]{value, targetType.getSimpleName(), e.getMessage()}), e);
        }
    }

    private static String normalizeValue(String value) {
        value = value.trim();
        // Remove BOM if present
        if (value.startsWith("\uFEFF")) {
            value = value.substring(1);
        }
        // Normalize Unicode
        return Normalizer.normalize(value, Normalizer.Form.NFKC);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object parseEnum(String value, Class<?> enumType) {
        String normalizedValue = value.toUpperCase(Locale.ROOT);
        return ENUM_CACHE
                .computeIfAbsent(enumType, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(normalizedValue, k -> {
                    try {
                        return Enum.valueOf((Class<Enum>) enumType, normalizedValue);
                    } catch (IllegalArgumentException e) {
                        // Try case-insensitive match
                        for (Object enumConstant : enumType.getEnumConstants()) {
                            if (enumConstant.toString().equalsIgnoreCase(value)) {
                                return enumConstant;
                            }
                        }
                        throw new SearchableParseException(
                                MessageUtils.getMessage("parser.enum.invalid",
                                        new Object[]{value, enumType.getSimpleName(),
                                        String.join(", ", getEnumNames(enumType))}));
                    }
                });
    }

    private static String[] getEnumNames(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(Object::toString)
                .toArray(String[]::new);
    }

    private static Boolean parseBoolean(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "true":
            case "1":
            case "yes":
            case "y":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "n":
            case "off":
                return false;
            default:
                throw new SearchableParseException(
                        MessageUtils.getMessage("parser.boolean.invalid", new Object[]{value}));
        }
    }

    private static Character parseCharacter(String value) {
        if (value.length() != 1) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.character.invalid", new Object[]{value}));
        }
        return value.charAt(0);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.uuid.invalid", new Object[]{value}), e);
        }
    }

    private static Object parseNumericValue(String value, Class<?> targetType) {
        try {
            // Remove grouping separators and normalize decimal separator
            value = value.replace(",", "").replace(" ", "");

            if (targetType == Byte.class || targetType == byte.class) {
                return Byte.parseByte(value);
            }
            if (targetType == Short.class || targetType == short.class) {
                return Short.parseShort(value);
            }
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(value);
            }
            if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(value);
            }
            if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(value);
            }
            if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(value);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(value);
            }
            if (targetType == BigInteger.class) {
                return new BigInteger(value);
            }
        } catch (NumberFormatException e) {
            throw new SearchableParseException(
                    MessageUtils.getMessage("parser.numeric.value.invalid",
                            new Object[]{value, targetType.getSimpleName(), e.getMessage()}), e);
        }
        throw new SearchableParseException(
                MessageUtils.getMessage("parser.numeric.type.unsupported", new Object[]{targetType.getSimpleName()}));
    }
}
