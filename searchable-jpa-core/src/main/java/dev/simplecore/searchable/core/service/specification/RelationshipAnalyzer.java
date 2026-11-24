package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.service.join.EntityPaths;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads an entity's relationships from the JPA metamodel: which to-one associations are fetched
 * with a list to prevent N+1 loading, and whether a path runs through a collection.
 */
public class RelationshipAnalyzer<T> {

    private final EntityManager entityManager;
    private final Class<T> entityClass;

    public RelationshipAnalyzer(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    /**
     * The associations fetched with every list of this entity: its own ManyToOne and OneToOne
     * attributes and, unless the persistence unit switches it off, the to-one attributes of the
     * entities in its collections (for example {@code comments.author}).
     */
    public Set<String> detectCommonToOneFields() {
        Set<String> fields = detectToOneFieldsForEntity(entityClass);
        if (SearchableJpaSettings.flag(entityManager, SearchableJpaSettings.AUTO_FETCH_COLLECTIONS, true)) {
            fields.addAll(detectNestedToOneRelationships());
        }
        return fields;
    }

    /**
     * The to-one attributes of the entities in this entity's OneToMany and ManyToMany collections,
     * as paths through the collection. Fetching them prevents N+1 loading when the collection's
     * elements are read, as in {@code UserAccount.organizations.parent}.
     */
    public Set<String> detectNestedToOneRelationships() {
        Set<String> nested = new HashSet<>();
        EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);
        for (PluralAttribute<? super T, ?, ?> collection : entityType.getPluralAttributes()) {
            Attribute.PersistentAttributeType kind = collection.getPersistentAttributeType();
            if (kind != Attribute.PersistentAttributeType.MANY_TO_MANY
                    && kind != Attribute.PersistentAttributeType.ONE_TO_MANY) {
                continue;
            }
            for (String toOne : detectToOneFieldsForEntity(collection.getElementType().getJavaType())) {
                nested.add(collection.getName() + "." + toOne);
            }
        }
        return nested;
    }

    /**
     * @param type an entity class
     * @return the names of its ManyToOne and OneToOne attributes
     */
    public Set<String> detectToOneFieldsForEntity(Class<?> type) {
        Set<String> toOne = new HashSet<>();
        entityManager.getMetamodel().entity(type).getSingularAttributes().forEach(attribute -> {
            Attribute.PersistentAttributeType kind = attribute.getPersistentAttributeType();
            if (kind == Attribute.PersistentAttributeType.MANY_TO_ONE
                    || kind == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                toOne.add(attribute.getName());
            }
        });
        return toOne;
    }

    /**
     * Checks if a path represents a ToMany relationship.
     *
     * @param root a root of the analysed entity; only its type is read
     * @param path a dotted attribute path
     * @return whether the path runs through a collection
     */
    public boolean isToManyPath(Root<T> root, String path) {
        return isToManyPath(path);
    }

    /**
     * @param path a dotted attribute path of the analysed entity
     * @return whether the path runs through a collection
     */
    public boolean isToManyPath(String path) {
        return path != null && !path.trim().isEmpty()
                && EntityPaths.traversesCollection(entityManager, entityClass, path);
    }
}
