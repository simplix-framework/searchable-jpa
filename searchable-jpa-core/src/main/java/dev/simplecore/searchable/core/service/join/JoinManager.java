package dev.simplecore.searchable.core.service.join;

import dev.simplecore.searchable.core.exception.SearchableJoinException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import java.util.Objects;
import lombok.NonNull;

/**
 * Resolves the field paths of search conditions against one query root, joining the associations
 * and collections a path runs through and reusing a join the root already has.
 *
 * @param <T> The root entity type
 */
public class JoinManager<T> {

    private final Root<T> root;
    private final EntityManager entityManager;
    private final Class<T> entityClass;

    /**
     * Creates a new JoinManager instance.
     *
     * @param entityManager The JPA EntityManager
     * @param root          The root entity
     * @throws SearchableJoinException if the EntityManager is not open
     */
    @SuppressWarnings("unchecked")
    public JoinManager(@NonNull EntityManager entityManager, @NonNull Root<T> root) {
        Objects.requireNonNull(entityManager, "EntityManager must not be null");
        if (!entityManager.isOpen()) {
            throw new SearchableJoinException(MessageUtils.getMessage("join.entity.manager.closed"));
        }
        this.root = root;
        this.entityManager = entityManager;
        this.entityClass = (Class<T>) root.getJavaType();
    }

    /** @return the entity class of the root */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /** @return the EntityManager the paths are resolved with */
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /** @return the root the paths are resolved against */
    public Root<T> getRoot() {
        return root;
    }

    /**
     * Gets the path of a field, creating the joins it runs through.
     *
     * <p>Every segment before the last is joined. The last segment is read as an attribute, unless
     * it is itself a collection of values, in which case it is joined and the elements are the path.
     *
     * @param path The field path (e.g., "author.name")
     * @return The Path object for the field
     * @throws SearchableJoinException when the path is empty or names an attribute that does not exist
     */
    public Path<?> getJoinPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new SearchableJoinException(MessageUtils.getMessage("join.path.required"));
        }
        String[] segments = path.split("\\.");
        try {
            From<?, ?> current = root;
            ManagedType<?> type = entityManager.getMetamodel().managedType(entityClass);
            for (int i = 0; i < segments.length; i++) {
                Attribute<?, ?> attribute = type.getAttribute(segments[i]);
                boolean last = i == segments.length - 1;
                if (last && !attribute.isCollection()) {
                    return current.get(segments[i]);
                }
                current = getOrCreateJoin(current, segments[i]);
                if (last) {
                    return current;
                }
                type = EntityPaths.typeBehind(entityManager, attribute);
            }
            return current;
        } catch (IllegalArgumentException e) {
            throw new SearchableJoinException(
                    MessageUtils.getMessage("join.path.invalid", new Object[]{path, entityClass.getSimpleName()}), e);
        }
    }

    /**
     * @param path a field path
     * @return whether the path runs through a collection, so that joining it yields one row per element
     * @throws SearchableJoinException when the path names an attribute that does not exist
     */
    public boolean traversesCollection(String path) {
        return EntityPaths.traversesCollection(entityManager, entityClass, path);
    }

    private Join<?, ?> getOrCreateJoin(From<?, ?> from, String attribute) {
        for (Join<?, ?> join : from.getJoins()) {
            if (join.getAttribute().getName().equals(attribute)) {
                return join;
            }
        }
        return from.join(attribute, JoinType.LEFT);
    }
}
