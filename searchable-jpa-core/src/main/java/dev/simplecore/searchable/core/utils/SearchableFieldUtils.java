package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.annotation.CollectionMatch;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.utils.SearchableDtoMetadata.SearchField;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SearchableFieldUtils {

    /**
     * Marker returned by {@link #getPrimaryKeyFieldName(EntityManager, Class)} for composite-key
     * entities ({@code @IdClass} or {@code @EmbeddedId}), signalling that composite-key handling is
     * required. It is also used as a deterministic-order tiebreaker placeholder in sort criteria and
     * must be stripped from any {@code Pageable} returned to callers.
     */
    public static final String COMPOSITE_KEY_MARKER = "__COMPOSITE_KEY__";

    // Keys by entity class name. The values hold names only, so an entry outlives neither a
    // reloaded class nor the persistence unit it was read from.
    private static final ConcurrentHashMap<String, EntityKey> ENTITY_KEYS = new ConcurrentHashMap<>();

    /**
     * Clears the cached entity keys. Use this method in test setup when using @DirtiesContext
     * to ensure test isolation.
     */
    public static void clearCache() {
        ENTITY_KEYS.clear();
    }

    /** An empty operator declaration allows every operator; value/type validation still applies. */
    public static SearchOperator[] getAllowedOperators(SearchableField field) {
        return field.operators().length == 0 ? SearchOperator.values() : field.operators();
    }

    public static String getEntityFieldFromDto(Class<?> dtoClass, String field) {
        if (dtoClass == null) {
            return field;
        }
        SearchField declared = SearchableDtoMetadata.of(dtoClass).field(field);
        return declared == null ? field : declared.entityField();
    }

    /**
     * Fills in what the DTO declares for the field a condition names: the entity path, whether text
     * comparisons ignore case, and how a path through a collection is matched. A condition on a
     * field the DTO does not declare keeps its own field name as the path.
     *
     * @param condition the condition to complete
     * @param dtoClass  the DTO the condition's field belongs to, or null to leave the name as the path
     */
    public static void applyDtoMapping(SearchCondition.Condition condition, Class<?> dtoClass) {
        SearchField declared = dtoClass == null ? null : SearchableDtoMetadata.of(dtoClass).field(condition.getField());
        if (declared == null) {
            condition.setEntityField(condition.getField());
            return;
        }
        condition.setEntityField(declared.entityField());
        condition.setCaseInsensitive(switch (declared.annotation().caseSensitivity()) {
            case INSENSITIVE -> Boolean.TRUE;
            case SENSITIVE -> Boolean.FALSE;
            case DEFAULT -> null;
        });
        CollectionMatch match = declared.annotation().match();
        condition.setMatch(match == CollectionMatch.JOINED ? null : match);
    }

    /**
     * Finds a declared field by name across the class hierarchy (most-derived class first).
     * Fields declared in a subclass take precedence over same-named fields in superclasses.
     *
     * @param type      the class to start searching from
     * @param fieldName the field name to locate
     * @return the field if found, or {@code null} if not present anywhere in the hierarchy
     */
    public static Field findFieldInHierarchy(Class<?> type, String fieldName) {
        Class<?> currentClass = type;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Collects all fields annotated with {@link SearchableField} across the class hierarchy.
     * Fields declared in a subclass take precedence over same-named fields in superclasses,
     * and each field name appears at most once. Iteration order lists subclass fields first.
     *
     * @param dtoClass the DTO class to scan
     * @return the annotated fields (empty if {@code dtoClass} is {@code null})
     */
    public static List<Field> getSearchableFields(Class<?> dtoClass) {
        if (dtoClass == null) {
            return new ArrayList<>();
        }
        List<Field> fields = new ArrayList<>();
        for (SearchField declared : SearchableDtoMetadata.of(dtoClass).fields()) {
            fields.add(declared.field());
        }
        return fields;
    }

    /**
     * Gets the sort field name from the DTO class using SearchableField annotation.
     * This method checks for sortField attribute first, then falls back to entityField or the field name itself.
     * 
     * @param dtoClass the DTO class
     * @param field the field name in the DTO
     * @return the field name to use for sorting
     */
    public static String getSortFieldFromDto(Class<?> dtoClass, String field) {
        if (dtoClass == null) {
            return field;
        }

        SearchField declared = SearchableDtoMetadata.of(dtoClass).field(field);
        return declared == null ? field : declared.sortField();
    }

    /**
     * @param entityManager an entity manager of the entity's persistence unit
     * @param entityClass the entity class
     * @return the entity's primary key as the JPA metamodel describes it
     */
    public static EntityKey entityKey(EntityManager entityManager, Class<?> entityClass) {
        return ENTITY_KEYS.computeIfAbsent(entityClass.getName(), name -> EntityKey.of(entityManager, entityClass));
    }

    /**
     * Gets the primary key field name from the entity class.
     * This is used to ensure unique, deterministic ordering during pagination.
     *
     * @param entityManager the entity manager
     * @param entityClass the entity class
     * @return the identifier attribute's name, or {@link #COMPOSITE_KEY_MARKER} for a composite key
     */
    public static String getPrimaryKeyFieldName(EntityManager entityManager, Class<?> entityClass) {
        EntityKey key = entityKey(entityManager, entityClass);
        return key.composite() ? COMPOSITE_KEY_MARKER : key.attribute();
    }

    /**
     * Checks if the entity uses @EmbeddedId composite key.
     *
     * @param entityManager the entity manager
     * @param entityClass the entity class
     * @return true if the entity uses @EmbeddedId, false otherwise
     */
    public static boolean isEmbeddedIdEntity(EntityManager entityManager, Class<?> entityClass) {
        return entityKey(entityManager, entityClass).embedded();
    }

    /**
     * Returns the actual attribute name of the {@code @EmbeddedId} property for an entity that uses
     * an embedded composite key. The name is resolved from the JPA metamodel, so it is not assumed
     * to be {@code "id"} and works for any attribute name (e.g. {@code "orderItemId"}).
     *
     * @param entityManager the entity manager
     * @param entityClass   the entity class
     * @return the embedded id attribute name, or {@code null} if the entity does not use {@code @EmbeddedId}
     */
    public static String getEmbeddedIdAttributeName(EntityManager entityManager, Class<?> entityClass) {
        EntityKey key = entityKey(entityManager, entityClass);
        return key.embedded() ? key.attribute() : null;
    }

    /**
     * Gets all ID field names for composite key entities (@IdClass and @EmbeddedId).
     *
     * @param entityManager the entity manager
     * @param entityClass the entity class
     * @return list of ID field names for composite keys, empty list if single ID
     */
    public static List<String> getCompositeKeyFieldNames(EntityManager entityManager, Class<?> entityClass) {
        return entityKey(entityManager, entityClass).components();
    }
}
