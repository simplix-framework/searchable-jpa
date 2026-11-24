package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the schema of each query parameter a search DTO field accepts.
 *
 * <p>Every schema is written inline. A reference into {@code components.schemas} would need the
 * component registered in the document, which an operation customizer cannot do, and an inline
 * schema is complete in every document it appears in.
 *
 * <p>Each schema carries its type in both forms swagger-models keeps, the single {@code type} that
 * an OpenAPI 3.0 document is written from and the {@code types} set that a 3.1 document is written
 * from, so the type is present whichever version SpringDoc emits.
 */
public class ParameterSchemaGenerator {

    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_ARRAY = "array";
    private static final String TYPE_OBJECT = "object";

    /**
     * Creates the schema of one field parameter for one operator.
     */
    public Schema<?> createFieldParameterSchema(Field field, SearchOperator operator) {
        Class<?> fieldType = field.getType();

        if (operator == SearchOperator.IS_NULL || operator == SearchOperator.IS_NOT_NULL) {
            return createNullCheckSchema();
        }
        if (operator == SearchOperator.IN || operator == SearchOperator.NOT_IN) {
            return createMultiValueSchema("Enter multiple values separated by comma", fieldType);
        }
        if (operator == SearchOperator.BETWEEN || operator == SearchOperator.NOT_BETWEEN) {
            return createMultiValueSchema("Enter two values separated by comma", fieldType);
        }
        if (fieldType.isEnum()) {
            return createEnumSchema(fieldType);
        }
        if (fieldType == String.class) {
            Schema<?> constrained = createStringSchemaWithEnumConstraints(field);
            if (constrained != null) {
                return constrained;
            }
        }
        return createInlineSchema(fieldType, field.getGenericType());
    }

    private static <S extends Schema<?>> S typed(S schema, String type) {
        schema.setType(type);
        schema.addType(type);
        return schema;
    }

    private Schema<?> createEnumSchema(Class<?> enumType) {
        Schema<String> schema = typed(new Schema<>(), TYPE_STRING);
        schema.setEnum(enumNames(enumType));
        return schema;
    }

    private static List<String> enumNames(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .collect(Collectors.toList());
    }

    private Schema<?> createInlineSchema(Class<?> fieldType, Type genericType) {
        if (fieldType.isArray()) {
            Schema<?> schema = typed(new Schema<>(), TYPE_ARRAY);
            schema.items(createSchemaForType(fieldType.getComponentType()));
            return schema;
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            Schema<?> schema = typed(new Schema<>(), TYPE_OBJECT);
            schema.additionalProperties(typeArgumentSchema(genericType, 1));
            return schema;
        }
        if (Collection.class.isAssignableFrom(fieldType)) {
            Schema<?> schema = typed(new Schema<>(), TYPE_ARRAY);
            schema.items(typeArgumentSchema(genericType, 0));
            return schema;
        }
        return createSchemaForType(fieldType);
    }

    private Schema<?> typeArgumentSchema(Type genericType, int index) {
        if (genericType instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length > index) {
            return createSchemaForType(parameterized.getActualTypeArguments()[index]);
        }
        return typed(new Schema<>(), TYPE_STRING);
    }

    /**
     * Creates the schema of a value type. A type with no entry here is sent as text.
     */
    private Schema<?> createSchemaForType(Type type) {
        Class<?> rawType = type instanceof ParameterizedType parameterized
                ? (Class<?>) parameterized.getRawType()
                : type instanceof Class<?> clazz ? clazz : String.class;

        if (rawType == LocalDateTime.class || rawType == Instant.class || rawType == OffsetDateTime.class
                || rawType == ZonedDateTime.class || rawType == Date.class) {
            // Absolute-instant and datetime search params: emit format:date-time so the
            // frontend codegen selects a date-time picker for these parameters.
            return typed(new Schema<>(), TYPE_STRING).format("date-time").description("Format: date-time (RFC 3339)");
        }
        if (rawType == LocalDate.class) {
            // Calendar-date search params: emit format:date for a date-only picker.
            return typed(new Schema<>(), TYPE_STRING).format("date").description("Format: yyyy-MM-dd");
        }
        if (rawType == LocalTime.class) {
            // Wall-clock time search params: emit format:partial-time so the frontend codegen
            // selects a time picker, matching the LocalTime response schema.
            return typed(new Schema<>(), TYPE_STRING).format("partial-time").description("Format: HH:mm[:ss]");
        }
        if (rawType == Long.class || rawType == long.class || rawType == BigInteger.class) {
            return typed(new Schema<>(), TYPE_INTEGER).format("int64");
        }
        if (rawType == Integer.class || rawType == int.class || rawType == Short.class || rawType == short.class
                || rawType == Byte.class || rawType == byte.class) {
            return typed(new Schema<>(), TYPE_INTEGER).format("int32");
        }
        if (rawType == Double.class || rawType == double.class || rawType == BigDecimal.class) {
            return typed(new Schema<>(), TYPE_NUMBER).format("double");
        }
        if (rawType == Float.class || rawType == float.class) {
            return typed(new Schema<>(), TYPE_NUMBER).format("float");
        }
        if (rawType == Boolean.class || rawType == boolean.class) {
            return typed(new Schema<>(), TYPE_BOOLEAN);
        }
        if (rawType == UUID.class) {
            return typed(new Schema<>(), TYPE_STRING).format("uuid");
        }
        if (rawType.isEnum()) {
            return createEnumSchema(rawType);
        }
        return typed(new Schema<>(), TYPE_STRING);
    }

    /**
     * The schema of {@code isNull} and {@code isNotNull}. Sending the parameter applies the check;
     * its value is not read, so the only value offered is {@code true}.
     */
    private Schema<?> createNullCheckSchema() {
        Schema<Boolean> schema = typed(new Schema<>(), TYPE_BOOLEAN);
        schema.setEnum(List.of(Boolean.TRUE));
        schema.setDescription("Send true to apply the check; the value itself is not read");
        return schema;
    }

    /**
     * Creates a comma-separated string schema used for multi-value operators (IN/NOT_IN/BETWEEN).
     * When the field is an enum, the allowed enum values are attached so the documentation still
     * lists them even though the input is entered as a comma-separated string.
     */
    private Schema<?> createMultiValueSchema(String description, Class<?> fieldType) {
        Schema<String> schema = typed(new Schema<>(), TYPE_STRING);
        schema.description(description);
        if (fieldType.isEnum()) {
            schema.setEnum(enumNames(fieldType));
        }
        return schema;
    }

    public Schema<?> createPageSchema() {
        return typed(new Schema<Integer>(), TYPE_INTEGER)
                .format("int32")
                .minimum(BigDecimal.ZERO)
                .description("Page number (0-based)");
    }

    public Schema<?> createSizeSchema() {
        return typed(new Schema<Integer>(), TYPE_INTEGER)
                .format("int32")
                .minimum(BigDecimal.ONE)
                .description("Items per page");
    }

    /**
     * The sort parameter: field.direction entries, sent as one comma-separated value.
     */
    public Schema<?> createSortSchema() {
        Schema<?> schema = typed(new Schema<>(), TYPE_ARRAY);
        schema.items(typed(new Schema<String>(), TYPE_STRING));
        return schema;
    }

    /**
     * Creates a String schema listing its allowed values when the field declares them, through
     * {@code @Schema(allowableValues)}, {@code @Schema(implementation = SomeEnum.class)} or a
     * {@code @Pattern} that is a plain list of alternatives.
     */
    private Schema<?> createStringSchemaWithEnumConstraints(Field field) {
        var schemaAnnotation = OpenApiDocUtils.getSchemaAnnotation(field);
        if (schemaAnnotation != null) {
            List<String> allowed = schemaAnnotation.allowableValues().length > 0
                    ? Arrays.asList(schemaAnnotation.allowableValues())
                    : schemaAnnotation.implementation().isEnum() ? enumNames(schemaAnnotation.implementation()) : List.of();
            if (!allowed.isEmpty()) {
                Schema<String> schema = typed(new Schema<>(), TYPE_STRING);
                schema.setEnum(allowed);
                if (!schemaAnnotation.description().isEmpty()) {
                    schema.description(schemaAnnotation.description());
                }
                return schema;
            }
        }
        List<String> alternatives = OpenApiDocUtils.patternAlternatives(field);
        if (alternatives.size() > 1) {
            Schema<String> schema = typed(new Schema<>(), TYPE_STRING);
            schema.setEnum(alternatives);
            schema.description("Pattern: " + field.getAnnotation(Pattern.class).regexp());
            return schema;
        }
        return null;
    }
}
