package dev.simplecore.searchable.core.utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.Hibernate;
import org.springframework.util.ReflectionUtils;

/**
 * The primary key of an entity as the JPA metamodel describes it.
 *
 * @param attribute the identifier attribute: the single {@code @Id} or the {@code @EmbeddedId};
 *                  null for an {@code @IdClass} entity, whose key attributes sit on the entity
 * @param embedded whether the identifier is an {@code @EmbeddedId}
 * @param components the attributes a composite key is made of, in the order their class declares
 *                   them; empty for a single key
 */
public record EntityKey(String attribute, boolean embedded, List<String> components) {

    /**
     * @param entityManager an entity manager of the entity's persistence unit
     * @param entityClass the entity
     * @return the entity's key
     * @throws IllegalArgumentException when the class is not an entity of the persistence unit
     */
    public static EntityKey of(EntityManager entityManager, Class<?> entityClass) {
        EntityType<?> entityType = entityManager.getMetamodel().entity(entityClass);
        if (!entityType.hasSingleIdAttribute()) {
            return new EntityKey(null, false, inDeclaredOrder(entityClass, names(entityType.getIdClassAttributes())));
        }
        SingularAttribute<?, ?> id = entityType.getId(entityType.getIdType().getJavaType());
        if (id.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED) {
            return new EntityKey(id.getName(), false, List.of());
        }
        EmbeddableType<?> embeddable = entityManager.getMetamodel().embeddable(id.getJavaType());
        return new EntityKey(id.getName(), true, inDeclaredOrder(id.getJavaType(), names(embeddable.getAttributes())));
    }

    /** @return whether the key has more than one part: an {@code @EmbeddedId} or an {@code @IdClass} */
    public boolean composite() {
        return embedded || attribute == null;
    }

    /**
     * @return the attribute paths of the key's parts from the entity: the identifier itself for a
     *         single key, otherwise one path per part
     */
    public List<String> paths() {
        if (!composite()) {
            return List.of(attribute);
        }
        if (!embedded) {
            return components;
        }
        List<String> paths = new ArrayList<>(components.size());
        for (String component : components) {
            paths.add(attribute + "." + component);
        }
        return paths;
    }

    /**
     * @param entityManager an entity manager of the entity's persistence unit
     * @param entityClass the entity class the key was read for
     * @param entity an instance of it
     * @return the values of the key's parts, in the order of {@link #paths()}
     */
    public List<Object> values(EntityManager entityManager, Class<?> entityClass, Object entity) {
        Object identifier = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        if (!composite()) {
            return List.of(identifier);
        }
        List<Object> values = new ArrayList<>(components.size());
        if (embedded) {
            EmbeddableType<?> embeddable = entityManager.getMetamodel().embeddable(identifier.getClass());
            for (String component : components) {
                values.add(read(embeddable.getAttribute(component).getJavaMember(), identifier));
            }
            return values;
        }
        // The parts of an @IdClass key are attributes of the entity itself. A proxy keeps its state
        // in the instance behind it.
        Object target = Hibernate.unproxy(entity);
        Set<? extends SingularAttribute<?, ?>> parts = entityManager.getMetamodel().entity(entityClass).getIdClassAttributes();
        for (String component : components) {
            for (SingularAttribute<?, ?> part : parts) {
                if (part.getName().equals(component)) {
                    values.add(read(part.getJavaMember(), target));
                }
            }
        }
        return values;
    }

    private static Object read(Member member, Object target) {
        if (member instanceof Method method) {
            ReflectionUtils.makeAccessible(method);
            return ReflectionUtils.invokeMethod(method, target);
        }
        Field field = (Field) member;
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, target);
    }

    private static Set<String> names(Set<? extends Attribute<?, ?>> attributes) {
        Set<String> names = new TreeSet<>();
        for (Attribute<?, ?> attribute : attributes) {
            names.add(attribute.getName());
        }
        return names;
    }

    /**
     * Orders attribute names as the class declares its fields, superclass first. A name without a
     * field of its own (an attribute mapped on its getter) follows, alphabetically. The metamodel
     * gives the names as a set, and the order has to be the same on every run because it is the
     * order the key is sorted and compared in.
     */
    private static List<String> inDeclaredOrder(Class<?> owner, Set<String> names) {
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            hierarchy.push(type);
        }
        List<String> ordered = new ArrayList<>(names.size());
        for (Class<?> type : hierarchy) {
            for (Field field : type.getDeclaredFields()) {
                if (names.contains(field.getName()) && !ordered.contains(field.getName())) {
                    ordered.add(field.getName());
                }
            }
        }
        for (String name : names) {
            if (!ordered.contains(name)) {
                ordered.add(name);
            }
        }
        return List.copyOf(ordered);
    }
}
