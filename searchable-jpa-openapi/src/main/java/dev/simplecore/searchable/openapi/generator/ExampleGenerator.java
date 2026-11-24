package dev.simplecore.searchable.openapi.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the example search condition shown beside a search endpoint: the first searchable field of
 * the DTO, with the first operator it allows.
 *
 * <p>The example is assembled directly and not through the validating builder. It illustrates the
 * shape of a request, and a made-up value cannot be expected to satisfy the constraints a DTO puts
 * on real ones.
 */
public class ExampleGenerator {

    private static final ObjectMapper EXAMPLE_READER = new ObjectMapper();
    private static final int EXAMPLE_PAGE = 0;
    private static final int EXAMPLE_SIZE = 10;

    /**
     * @param dtoClass the search DTO
     * @return the example condition as JSON text
     */
    public String generateSimpleExample(Class<?> dtoClass) {
        try {
            return buildSimpleCondition(dtoClass).toJson();
        } catch (JsonProcessingException e) {
            throw new SearchableOperationException(
                    MessageUtils.getMessage("openapi.example.failed", new Object[]{dtoClass.getName()}), e);
        }
    }

    /**
     * @param dtoClass the search DTO
     * @return the example condition as a JSON object, so a document carries it as structured data
     */
    public Map<String, Object> generateExampleObject(Class<?> dtoClass) {
        try {
            return EXAMPLE_READER.readValue(generateSimpleExample(dtoClass),
                    new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new SearchableOperationException(
                    MessageUtils.getMessage("openapi.example.failed", new Object[]{dtoClass.getName()}), e);
        }
    }

    private SearchCondition<?> buildSimpleCondition(Class<?> dtoClass) {
        SearchCondition<Object> condition = new SearchCondition<>();
        List<Field> searchableFields = SearchableFieldUtils.getSearchableFields(dtoClass);
        if (!searchableFields.isEmpty()) {
            condition.getNodes().add(exampleCondition(searchableFields.get(0)));
        }
        condition.setPage(EXAMPLE_PAGE);
        condition.setSize(EXAMPLE_SIZE);
        return condition;
    }

    private SearchCondition.Condition exampleCondition(Field field) {
        SearchOperator operator = SearchableFieldUtils.getAllowedOperators(field.getAnnotation(SearchableField.class))[0];
        Object value = null;
        Object value2 = null;
        switch (operator) {
            case BETWEEN:
            case NOT_BETWEEN:
                List<Object> range = exampleRange(field, operator);
                value = range.get(0);
                value2 = range.get(1);
                break;
            case IN:
            case NOT_IN:
                value = exampleValues(field, operator);
                break;
            case IS_NULL:
            case IS_NOT_NULL:
                break;
            case CONTAINS:
            case NOT_CONTAINS:
            case STARTS_WITH:
            case NOT_STARTS_WITH:
            case ENDS_WITH:
            case NOT_ENDS_WITH:
                value = String.valueOf(OpenApiDocUtils.getExampleValue(field));
                break;
            default:
                value = convertToTargetType(OpenApiDocUtils.getExampleValue(field), field.getType());
        }
        return new SearchCondition.Condition(null, field.getName(), operator, value, value2, null);
    }

    /**
     * Produces the two distinct bounds for a BETWEEN/NOT_BETWEEN example, converting each to the
     * field's type.
     */
    private List<Object> exampleRange(Field field, SearchOperator operator) {
        List<Object> converted = exampleValues(field, operator);
        while (converted.size() < 2) {
            converted.add(convertToTargetType(OpenApiDocUtils.getExampleValue(field), field.getType()));
        }
        return converted;
    }

    /**
     * Produces the example values for a multi-value operator (IN/NOT_IN/BETWEEN), converting each
     * element to the field's type.
     */
    private List<Object> exampleValues(Field field, SearchOperator operator) {
        Object example = OpenApiDocUtils.getExampleValue(field, operator);
        List<Object> values = new ArrayList<>();
        if (example instanceof List) {
            for (Object element : (List<?>) example) {
                values.add(convertToTargetType(element, field.getType()));
            }
        } else if (example != null) {
            values.add(convertToTargetType(example, field.getType()));
        }
        return values;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object convertToTargetType(Object rawExampleValue, Class<?> fieldType) {
        // Convert numeric example values (e.g. the generic [1, 100] BETWEEN range) to the field's type.
        if (rawExampleValue instanceof Number && !fieldType.isInstance(rawExampleValue)) {
            Number number = (Number) rawExampleValue;
            if (fieldType == Long.class || fieldType == long.class) {
                return number.longValue();
            } else if (fieldType == Integer.class || fieldType == int.class) {
                return number.intValue();
            } else if (fieldType == Double.class || fieldType == double.class) {
                return number.doubleValue();
            } else if (fieldType == Float.class || fieldType == float.class) {
                return number.floatValue();
            } else if (fieldType == Short.class || fieldType == short.class) {
                return number.shortValue();
            } else if (fieldType == Byte.class || fieldType == byte.class) {
                return number.byteValue();
            } else if (fieldType == BigDecimal.class) {
                return new BigDecimal(number.toString());
            } else if (fieldType == BigInteger.class) {
                return BigInteger.valueOf(number.longValue());
            }
        }
        if (rawExampleValue instanceof String) {
            String strValue = (String) rawExampleValue;
            if (fieldType == Long.class || fieldType == long.class) {
                return Long.parseLong(strValue);
            } else if (fieldType == Integer.class || fieldType == int.class) {
                return Integer.parseInt(strValue);
            } else if (fieldType == Double.class || fieldType == double.class) {
                return Double.parseDouble(strValue);
            } else if (fieldType == Float.class || fieldType == float.class) {
                return Float.parseFloat(strValue);
            } else if (fieldType == Short.class || fieldType == short.class) {
                return Short.parseShort(strValue);
            } else if (fieldType == Byte.class || fieldType == byte.class) {
                return Byte.parseByte(strValue);
            } else if (fieldType == BigDecimal.class) {
                return new BigDecimal(strValue);
            } else if (fieldType == BigInteger.class) {
                return new BigInteger(strValue);
            } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                return Boolean.parseBoolean(strValue);
            } else if (fieldType == Character.class || fieldType == char.class) {
                return !strValue.isEmpty() ? strValue.charAt(0) : '\0';
            } else if (fieldType == LocalDateTime.class) {
                return LocalDateTime.parse(strValue);
            } else if (fieldType == LocalDate.class) {
                return LocalDate.parse(strValue);
            } else if (fieldType == LocalTime.class) {
                return LocalTime.parse(strValue);
            } else if (fieldType == ZonedDateTime.class) {
                return ZonedDateTime.parse(strValue);
            } else if (fieldType == OffsetDateTime.class) {
                return OffsetDateTime.parse(strValue);
            } else if (fieldType == Instant.class) {
                return Instant.parse(strValue);
            } else if (fieldType == Date.class) {
                return Date.from(Instant.parse(strValue));
            } else if (SqlDateExample.supports(fieldType)) {
                return SqlDateExample.parse(strValue);
            } else if (fieldType == Timestamp.class) {
                return Timestamp.valueOf(LocalDateTime.parse(strValue));
            } else if (fieldType == UUID.class) {
                return UUID.fromString(strValue);
            } else if (fieldType.isEnum()) {
                return Enum.valueOf((Class) fieldType, strValue);
            }
        }
        return rawExampleValue;
    }
}
