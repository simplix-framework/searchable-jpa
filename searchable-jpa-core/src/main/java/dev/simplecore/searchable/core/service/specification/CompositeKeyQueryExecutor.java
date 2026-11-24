package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.service.join.EntityPaths;
import dev.simplecore.searchable.core.utils.EntityKey;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Handles two-phase pagination for composite-key entities ({@code @IdClass} and {@code @EmbeddedId}).
 *
 * <p>The key's attributes come from the JPA metamodel, so neither the name of an
 * {@code @EmbeddedId} attribute nor the names of its parts are assumed. A key travels between the
 * phases as the list of its parts' values, in the order of {@link EntityKey#components()}.
 */
class CompositeKeyQueryExecutor<T> {

    private final SearchFilter<T> filter;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final EntityKey key;

    CompositeKeyQueryExecutor(SearchFilter<T> filter,
                              EntityManager entityManager,
                              Class<T> entityClass,
                              JpaSpecificationExecutor<T> specificationExecutor) {
        this.filter = filter;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
        this.key = SearchableFieldUtils.entityKey(entityManager, entityClass);
    }

    /** @return how many columns one key binds in a statement */
    int keyColumns() {
        return key.components().size();
    }

    private Path<?> keyPath(Root<T> root, String component) {
        return key.embedded() ? root.get(key.attribute()).get(component) : root.get(component);
    }

    /**
     * Phase 1: the keys of the requested page. A sort through a collection joins it, and the rows
     * are then grouped back to one per key with a deterministic aggregate of each sort value.
     */
    List<Object> executePhaseOne(PageRequest pageRequest) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<T> root = query.from(entityClass);

        List<Sort.Order> sortOrders = pageRequest.getSort().stream()
                .filter(order -> !SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(order.getProperty()))
                .collect(Collectors.toList());
        boolean sortThroughCollection = sortOrders.stream()
                .anyMatch(order -> EntityPaths.traversesCollection(entityManager, entityClass, order.getProperty()));

        List<Selection<?>> selections = new ArrayList<>();
        List<Expression<?>> keyPaths = new ArrayList<>();
        for (String component : key.components()) {
            Path<?> path = keyPath(root, component);
            selections.add(path);
            keyPaths.add(path);
        }

        List<Order> orders = new ArrayList<>();
        Set<String> sortedComponents = new LinkedHashSet<>();
        for (Sort.Order sortOrder : sortOrders) {
            String property = sortOrder.getProperty();
            Expression<?> sortValue;
            int keyPart = key.paths().indexOf(property);
            if (keyPart >= 0) {
                String component = key.components().get(keyPart);
                sortValue = keyPath(root, component);
                sortedComponents.add(component);
            } else {
                Path<?> sortPath = SpecificationQuerySupport.getPath(root, property);
                sortValue = sortThroughCollection
                        ? SpecificationQuerySupport.aggregateForSort(cb, sortPath, sortOrder.isAscending())
                        : sortPath;
                if (sortThroughCollection) {
                    // A database orders a grouped result only by what it selects.
                    selections.add(sortValue);
                }
            }
            orders.add(sortOrder.isAscending() ? cb.asc(sortValue) : cb.desc(sortValue));
        }
        // Deterministic tiebreaker: every part of the key not already ordered.
        for (String component : key.components()) {
            if (!sortedComponents.contains(component)) {
                orders.add(cb.asc(keyPath(root, component)));
            }
        }

        query.multiselect(selections);
        Predicate matching = sortThroughCollection
                ? filter.matchingJoined(root, query, cb)
                : filter.matching(root, query, cb);
        if (matching != null) {
            query.where(matching);
        }
        if (sortThroughCollection) {
            query.groupBy(keyPaths);
        }
        query.orderBy(orders);

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(Math.toIntExact(pageRequest.getOffset()));
        typedQuery.setMaxResults(pageRequest.getPageSize());

        int parts = key.components().size();
        return typedQuery.getResultList().stream()
                .map(row -> (Object) List.copyOf(Arrays.asList(row).subList(0, parts)))
                .collect(Collectors.toList());
    }

    /**
     * Phase 2: the entities of the given keys with their fetch joins. Ordering is restored by the
     * caller from the Phase 1 key order, so no {@code ORDER BY} is applied here.
     */
    List<T> executePhaseTwo(List<Object> keys, Set<String> fetchFields) {
        Specification<T> spec = (root, query, cb) -> {
            SpecificationQuerySupport.applyFetchJoins(root, query, fetchFields);
            List<Predicate> anyKey = new ArrayList<>(keys.size());
            for (Object each : keys) {
                List<?> values = (List<?>) each;
                Predicate[] parts = new Predicate[values.size()];
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = cb.equal(keyPath(root, key.components().get(i)), values.get(i));
                }
                anyKey.add(cb.and(parts));
            }
            return cb.or(anyKey.toArray(new Predicate[0]));
        };
        return specificationExecutor.findAll(spec, Sort.unsorted());
    }

    /**
     * @return the key of a loaded entity in the representation {@link #executePhaseOne} produces
     */
    Object extractId(T entity) {
        return key.values(entityManager, entityClass, entity);
    }
}
