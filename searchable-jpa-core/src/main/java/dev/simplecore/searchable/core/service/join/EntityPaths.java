package dev.simplecore.searchable.core.service.join;

import dev.simplecore.searchable.core.exception.SearchableJoinException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;

/**
 * Reads dotted attribute paths against the JPA metamodel.
 */
public final class EntityPaths {

    private EntityPaths() {
    }

    /**
     * @param entityManager an entity manager of the persistence unit
     * @param entityClass the entity the path starts from
     * @param path a dotted attribute path
     * @return whether the path runs through a collection, so that joining it yields one row per element
     * @throws SearchableJoinException when the path names an attribute that does not exist
     */
    public static boolean traversesCollection(EntityManager entityManager, Class<?> entityClass, String path) {
        try {
            ManagedType<?> type = entityManager.getMetamodel().managedType(entityClass);
            String[] segments = path.split("\\.");
            for (int i = 0; i < segments.length; i++) {
                Attribute<?, ?> attribute = type.getAttribute(segments[i]);
                if (attribute.isCollection()) {
                    return true;
                }
                if (i < segments.length - 1) {
                    type = typeBehind(entityManager, attribute);
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            throw new SearchableJoinException(
                    MessageUtils.getMessage("join.path.invalid", new Object[]{path, entityClass.getSimpleName()}), e);
        }
    }

    /**
     * @param entityManager an entity manager of the persistence unit
     * @param entityClass the entity the path starts from
     * @param path a dotted attribute path
     * @return the Java type of the attribute the path ends at; the element type when it is a collection
     * @throws SearchableJoinException when the path names an attribute that does not exist
     */
    public static Class<?> javaType(EntityManager entityManager, Class<?> entityClass, String path) {
        try {
            ManagedType<?> type = entityManager.getMetamodel().managedType(entityClass);
            String[] segments = path.split("\\.");
            Attribute<?, ?> attribute = type.getAttribute(segments[0]);
            for (int i = 1; i < segments.length; i++) {
                attribute = typeBehind(entityManager, attribute).getAttribute(segments[i]);
            }
            return attribute instanceof PluralAttribute<?, ?, ?> plural
                    ? plural.getElementType().getJavaType()
                    : attribute.getJavaType();
        } catch (IllegalArgumentException e) {
            throw new SearchableJoinException(
                    MessageUtils.getMessage("join.path.invalid", new Object[]{path, entityClass.getSimpleName()}), e);
        }
    }

    /**
     * @return the managed type an attribute leads to: the element type of a collection, otherwise
     *         the attribute's own type
     * @throws IllegalArgumentException when the attribute's type is not a managed type
     */
    static ManagedType<?> typeBehind(EntityManager entityManager, Attribute<?, ?> attribute) {
        Class<?> target = attribute instanceof PluralAttribute<?, ?, ?> plural
                ? plural.getElementType().getJavaType()
                : attribute.getJavaType();
        return entityManager.getMetamodel().managedType(target);
    }
}
