package dev.simplecore.searchable.core.utils;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

/**
 * Utility class to detect JSON-typed fields in JPA entities.
 * JSON-typed fields are stored as TEXT/VARCHAR in the database but represented
 * as complex Java types (Map, List) in the entity.
 */
public final class JsonTypeDetector {

    private static final Map<String, Boolean> TYPE_CACHE = new ConcurrentHashMap<>();

    private JsonTypeDetector() {
    }

    /**
     * Checks if a field is JSON-typed (stored as TEXT/VARCHAR in DB but complex type in Java).
     *
     * @param entityManager The JPA EntityManager
     * @param entityClass   The entity class
     * @param fieldPath     The field path (supports nested paths like "author.metadata")
     * @return true if the field is JSON-typed
     */
    public static boolean isJsonTypedField(EntityManager entityManager,
                                           Class<?> entityClass,
                                           String fieldPath) {
        if (entityClass == null || fieldPath == null || fieldPath.isEmpty()) {
            return false;
        }

        String cacheKey = entityClass.getName() + "#" + fieldPath;
        return TYPE_CACHE.computeIfAbsent(cacheKey, k -> detectJsonType(entityClass, fieldPath));
    }

    private static boolean detectJsonType(Class<?> entityClass, String fieldPath) {
        String[] pathParts = fieldPath.split("\\.");
        Class<?> currentClass = entityClass;

        for (int i = 0; i < pathParts.length; i++) {
            String fieldName = pathParts[i];
            Field field = findField(currentClass, fieldName);

            if (field == null) {
                return false;
            }

            if (i == pathParts.length - 1) {
                return isJsonField(field);
            }

            currentClass = field.getType();
        }

        return false;
    }

    private static boolean isJsonField(Field field) {
        Class<?> fieldType = field.getType();

        if (!isComplexType(fieldType)) {
            return false;
        }

        if (hasHibernateJsonType(field)) {
            return true;
        }

        if (hasJpaJsonConverter(field)) {
            return true;
        }

        if (hasJdbcTypeCodeJson(field)) {
            return true;
        }

        if (hasJsonColumnDefinition(field)) {
            return true;
        }

        return false;
    }

    private static boolean isComplexType(Class<?> type) {
        return Map.class.isAssignableFrom(type) ||
               Collection.class.isAssignableFrom(type) ||
               type.isArray();
    }

    private static boolean hasHibernateJsonType(Field field) {
        Type type = field.getAnnotation(Type.class);
        return type != null && nameMentionsJson(type.value());
    }

    private static boolean hasJpaJsonConverter(Field field) {
        Convert convert = field.getAnnotation(Convert.class);
        return convert != null && nameMentionsJson(convert.converter());
    }

    private static boolean hasJdbcTypeCodeJson(Field field) {
        JdbcTypeCode jdbcTypeCode = field.getAnnotation(JdbcTypeCode.class);
        if (jdbcTypeCode == null) {
            return false;
        }
        int code = jdbcTypeCode.value();
        return code == SqlTypes.JSON || code == SqlTypes.LONGVARCHAR || code == SqlTypes.LONGNVARCHAR;
    }

    private static boolean hasJsonColumnDefinition(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null) {
            String colDef = columnAnnotation.columnDefinition().toLowerCase(Locale.ROOT);
            return colDef.contains("json") || colDef.contains("text");
        }
        return false;
    }

    private static boolean nameMentionsJson(Class<?> type) {
        return type.getName().toLowerCase(Locale.ROOT).contains("json");
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && !current.equals(Object.class)) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Clears the type detection cache.
     * Useful for testing purposes.
     */
    public static void clearCache() {
        TYPE_CACHE.clear();
    }
}