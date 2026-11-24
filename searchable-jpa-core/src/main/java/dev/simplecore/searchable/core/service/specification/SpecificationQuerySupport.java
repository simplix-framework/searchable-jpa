package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.exception.SearchableJoinException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.Set;

/**
 * Shared Criteria helpers used by the query executors: resolving (possibly nested) sort paths,
 * applying fetch joins, and producing deterministic aggregate sort expressions.
 */
final class SpecificationQuerySupport {

    private SpecificationQuerySupport() {
    }

    /**
     * Resolves a (possibly nested) property path against the given root, creating LEFT JOINs for
     * intermediate relationship segments and reusing existing joins where possible.
     */
    static Path<?> getPath(From<?, ?> from, String property) {
        String[] parts = property.split("\\.");
        From<?, ?> current = from;
        for (int i = 0; i < parts.length - 1; i++) {
            Join<?, ?> join = null;
            for (Join<?, ?> existingJoin : current.getJoins()) {
                if (existingJoin.getAttribute().getName().equals(parts[i])) {
                    join = existingJoin;
                    break;
                }
            }
            current = join != null ? join : current.join(parts[i], JoinType.LEFT);
        }
        return current.get(parts[parts.length - 1]);
    }

    /**
     * Produces a deterministic aggregate over a sort path so that traversing a to-many relationship
     * for sorting collapses to a single value per group ({@code least} for ascending, {@code greatest}
     * for descending). This keeps ID-level pagination stable even when the sort path is to-many.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Expression<?> aggregateForSort(CriteriaBuilder cb, Path<?> path, boolean ascending) {
        return ascending ? cb.least((Expression) path) : cb.greatest((Expression) path);
    }

    /**
     * Whether a query returns the entity itself. Only such a query can carry fetch joins: Hibernate
     * refuses a fetch whose owner is absent from the select list, which covers a count and equally
     * a grouped aggregate over the same conditions.
     */
    static boolean selectsEntity(Root<?> root, CriteriaQuery<?> query) {
        return query.getResultType() != null && root.getJavaType().isAssignableFrom(query.getResultType());
    }

    /**
     * Applies fetch joins for the given fields (supporting nested {@code a.b.c} paths) to a query
     * that returns the entity, reusing a fetch the root already has.
     *
     * @throws SearchableJoinException when a field does not name an association of the entity
     */
    static void applyFetchJoins(Root<?> root, CriteriaQuery<?> query, Set<String> fetchFields) {
        if (fetchFields == null || fetchFields.isEmpty() || !selectsEntity(root, query)) {
            return;
        }
        for (String fetchField : fetchFields) {
            try {
                From<?, ?> current = root;
                for (String part : fetchField.split("\\.")) {
                    current = fetchOnce(current, part);
                }
            } catch (IllegalArgumentException | ClassCastException e) {
                throw new SearchableJoinException(MessageUtils.getMessage("join.path.invalid",
                        new Object[]{fetchField, root.getJavaType().getSimpleName()}), e);
            }
        }
    }

    private static From<?, ?> fetchOnce(From<?, ?> from, String attribute) {
        return (From<?, ?>) from.getFetches().stream()
                .filter(fetch -> fetch.getAttribute().getName().equals(attribute))
                .findFirst()
                .orElseGet(() -> from.fetch(attribute, JoinType.LEFT));
    }
}
