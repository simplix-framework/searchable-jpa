package dev.simplecore.searchable.openapi.utils;

import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class OpenApiDocUtils {

    private static final LocalDateTime FIXED_DATE_TIME = LocalDateTime.of(2025, 5, 1, 0, 0, 0);
    private static final LocalDate FIXED_DATE = LocalDate.of(2025, 5, 1);
    private static final LocalTime FIXED_TIME = LocalTime.of(0, 0, 0);
    private static final Instant FIXED_INSTANT = FIXED_DATE_TIME.toInstant(ZoneOffset.UTC);
    private static final int RANGE_EXAMPLE_DAYS = 7;

    private OpenApiDocUtils() {
    }

    public static Schema getSchemaAnnotation(Field field) {
        return field.getAnnotation(Schema.class);
    }

    public static String getFieldDescription(Field field) {
        Schema schema = field.getAnnotation(Schema.class);
        return schema != null && !schema.description().isEmpty() ? schema.description() : field.getName();
    }

    /**
     * The values a {@code @Pattern} allows when the expression is a plain list of alternatives
     * ({@code ACTIVE|INACTIVE}) or one character out of a set ({@code [YN]}).
     *
     * @return the allowed values, or an empty list when the pattern is anything else
     */
    public static List<String> patternAlternatives(Field field) {
        Pattern pattern = field.getAnnotation(Pattern.class);
        return pattern == null ? List.of() : PatternAlternatives.of(pattern.regexp());
    }

    public static Object getExampleValue(Field field) {
        Schema schema = field.getAnnotation(Schema.class);
        if (schema != null && !schema.example().isEmpty()) {
            return schema.example();
        }
        return getDefaultExampleValue(field);
    }

    public static Object getExampleValue(Field field, SearchOperator operator) {
        if (operator == SearchOperator.BETWEEN || operator == SearchOperator.NOT_BETWEEN) {
            return getBetweenExampleValue(field);
        } else if (isInOperation(operator)) {
            return getInExampleValue(field);
        }
        return getExampleValue(field);
    }

    private static boolean isInOperation(SearchOperator operator) {
        return operator == SearchOperator.IN || operator == SearchOperator.NOT_IN;
    }

    /**
     * Two distinct bounds in the field's own type, so the range example is meaningful.
     */
    private static Object getBetweenExampleValue(Field field) {
        Class<?> type = field.getType();
        if (type == LocalDateTime.class) {
            return List.of(FIXED_DATE_TIME, FIXED_DATE_TIME.plusDays(RANGE_EXAMPLE_DAYS));
        }
        if (type == LocalDate.class) {
            return List.of(FIXED_DATE, FIXED_DATE.plusDays(RANGE_EXAMPLE_DAYS));
        }
        if (type == LocalTime.class) {
            return List.of(FIXED_TIME, FIXED_TIME.plusHours(RANGE_EXAMPLE_DAYS));
        }
        if (type == Instant.class) {
            return List.of(FIXED_INSTANT, FIXED_INSTANT.plus(Duration.ofDays(RANGE_EXAMPLE_DAYS)));
        }
        if (type == OffsetDateTime.class) {
            OffsetDateTime start = FIXED_DATE_TIME.atOffset(ZoneOffset.UTC);
            return List.of(start, start.plusDays(RANGE_EXAMPLE_DAYS));
        }
        if (type == ZonedDateTime.class) {
            ZonedDateTime start = FIXED_DATE_TIME.atZone(ZoneOffset.UTC);
            return List.of(start, start.plusDays(RANGE_EXAMPLE_DAYS));
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return List.of(1, 100);
        }
        Object single = getExampleValue(field);
        return List.of(single, single);
    }

    private static Object getInExampleValue(Field field) {
        List<String> alternatives = patternAlternatives(field);
        if (alternatives.size() > 1) {
            return new ArrayList<>(alternatives.subList(0, 2));
        }
        List<Object> objects = new ArrayList<>();
        objects.add(getExampleValue(field));
        return objects;
    }

    private static Object getDefaultExampleValue(Field field) {
        Class<?> type = field.getType();
        if (type == String.class) return "example";
        if (type == Integer.class || type == int.class) return 1;
        if (type == Long.class || type == long.class) return 1L;
        if (type == Double.class || type == double.class) return 1.0;
        if (type == Float.class || type == float.class) return 1.0f;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == LocalDateTime.class) return FIXED_DATE_TIME;
        if (type == LocalDate.class) return FIXED_DATE;
        if (type == LocalTime.class) return FIXED_TIME;
        if (type == Instant.class) return FIXED_INSTANT;
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (Collection.class.isAssignableFrom(type)) {
            return getCollectionElementExampleValue(field);
        }
        return "example";
    }

    private static Object getCollectionElementExampleValue(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] typeArgs = parameterizedType.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> elementType) {
                if (elementType == String.class) return "example";
                if (elementType == Integer.class) return 1;
                if (elementType == Long.class) return 1L;
                if (elementType.isEnum()) return elementType.getEnumConstants()[0];
            }
        }
        return "example";
    }

    public static String getOperationDescription(SearchOperator operator) {
        return operator.getName();
    }
}
