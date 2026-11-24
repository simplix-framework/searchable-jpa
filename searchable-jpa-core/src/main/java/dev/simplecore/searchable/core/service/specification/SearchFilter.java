package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.annotation.CollectionMatch;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.join.EntityPaths;
import dev.simplecore.searchable.core.service.join.JoinManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * The rows a search selects, as one predicate that can stand in any query over the entity.
 *
 * <p>A condition on a field behind a collection is applied by joining the collection, which yields
 * one row per matching element. Wherever such a join would be made, the whole condition tree is
 * moved into an {@code EXISTS} over the entity again and tied to the outer row. The outer query then
 * has one row per entity whatever the conditions reach into, so it needs no {@code DISTINCT} and
 * its counts, sums and pages are those of the entities.
 *
 * <p>A scope the server adds is applied to the outer row and combined with AND, so no condition a
 * client sent can widen it. A scope that joins a collection or adds a fetch join is refused.
 *
 * @param <T> the entity type
 */
final class SearchFilter<T> {

    private final SearchCondition<?> condition;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final Specification<T> scope;
    private Boolean joinsCollection;

    SearchFilter(SearchCondition<?> condition, EntityManager entityManager, Class<T> entityClass,
                 Specification<T> scope) {
        this.condition = condition;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.scope = scope;
    }

    /** @return whether nothing narrows the rows: no condition and no scope */
    boolean isUnfiltered() {
        return !hasConditions() && scope == null;
    }

    private boolean hasConditions() {
        return condition.getNodes() != null && !condition.getNodes().isEmpty();
    }

    /**
     * @return whether a condition is applied by joining a collection. A condition matched per
     *         element carries its own subquery and joins nothing.
     */
    boolean joinsCollection() {
        if (joinsCollection == null) {
            joinsCollection = hasConditions() && joinsCollection(condition.getNodes());
        }
        return joinsCollection;
    }

    private boolean joinsCollection(List<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof SearchCondition.Group group) {
                if (group.getNodes() != null && joinsCollection(group.getNodes())) {
                    return true;
                }
            } else if (node instanceof SearchCondition.Condition leaf) {
                CollectionMatch match = leaf.getMatch();
                String path = leaf.getEntityField() != null && !leaf.getEntityField().isEmpty()
                        ? leaf.getEntityField() : leaf.getField();
                if (path != null && (match == null || match == CollectionMatch.JOINED)
                        && EntityPaths.traversesCollection(entityManager, entityClass, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param root the root of the query being filtered
     * @param query that query
     * @param cb the criteria builder
     * @return the predicate selecting the matching rows, or null when every row matches
     */
    Predicate matching(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return combined(root, query, cb, joinsCollection());
    }

    /**
     * The same rows, with the conditions applied to the root itself. A condition behind a collection
     * then joins it and repeats the entity once per matching element, so this form is only for a
     * query that groups its rows back to one per entity. In return, a sort through the same
     * collection reads the elements the conditions matched.
     *
     * @param root the root of the grouped query being filtered
     * @param query that query
     * @param cb the criteria builder
     * @return the predicate selecting the matching rows, or null when every row matches
     */
    Predicate matchingJoined(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return combined(root, query, cb, false);
    }

    private Predicate combined(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb, boolean inSubquery) {
        List<Predicate> predicates = new ArrayList<>();
        if (hasConditions()) {
            if (inSubquery) {
                Subquery<Integer> matching = query.subquery(Integer.class);
                Root<T> candidate = matching.from(entityClass);
                matching.select(cb.literal(1)).where(cb.equal(candidate, root), tree(candidate, matching, cb));
                predicates.add(cb.exists(matching));
            } else {
                predicates.add(tree(root, query, cb));
            }
        }
        if (scope != null) {
            Predicate scoped = scoped(root, query, cb);
            if (scoped != null) {
                predicates.add(scoped);
            }
        }
        if (predicates.isEmpty()) {
            return null;
        }
        return predicates.size() == 1 ? predicates.get(0) : cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Applies the scope to the row being filtered and refuses one that changes how many rows the
     * query has: a join through a collection repeats the entity once per element, and nothing in
     * the query removes the repeats, so counts, sums and pages would silently grow.
     */
    private Predicate scoped(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Set<Join<?, ?>> collectionJoins = collectionJoins(root);
        int fetches = root.getFetches().size();
        Predicate predicate = scope.toPredicate(root, query, cb);
        Set<Join<?, ?>> added = collectionJoins(root);
        added.removeAll(collectionJoins);
        if (!added.isEmpty() || root.getFetches().size() != fetches) {
            throw new SearchableConfigurationException(
                    MessageUtils.getMessage("scope.join.unsupported", new Object[]{entityClass.getSimpleName()}));
        }
        return predicate;
    }

    private static Set<Join<?, ?>> collectionJoins(From<?, ?> from) {
        Set<Join<?, ?>> joins = Collections.newSetFromMap(new IdentityHashMap<>());
        collectCollectionJoins(from, joins);
        return joins;
    }

    private static void collectCollectionJoins(From<?, ?> from, Set<Join<?, ?>> into) {
        for (Join<?, ?> join : from.getJoins()) {
            if (join.getAttribute().isCollection()) {
                into.add(join);
            }
            collectCollectionJoins(join, into);
        }
    }

    /** @return this filter as a specification for a repository query */
    Specification<T> asSpecification() {
        return this::matching;
    }

    private Predicate tree(Root<T> root, AbstractQuery<?> query, CriteriaBuilder cb) {
        PredicateBuilder<T> predicates = new PredicateBuilder<>(cb, new JoinManager<>(entityManager, root), query);
        SpecificationBuilder<T> nodes = new SpecificationBuilder<>(predicates);
        return ConditionTree.fold(condition.getNodes(), cb, node -> nodes.build(root, null, cb, node));
    }
}
