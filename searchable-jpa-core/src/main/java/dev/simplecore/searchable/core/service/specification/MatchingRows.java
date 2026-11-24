package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Narrows an aggregate query to the rows a list's {@link SearchCondition} matches.
 *
 * <p>A condition on a joined collection would multiply each entity by its matching children, and
 * every count and sum read over it would be inflated. Such a condition is therefore applied inside
 * an {@code EXISTS} over a second root, which leaves the aggregate's own root at one row per entity.
 */
public final class MatchingRows {

    private MatchingRows() {
    }

    /**
     * @param cb the criteria builder of the aggregate query
     * @param query the aggregate query the predicate is for
     * @param root the aggregate query's root
     * @param entityType the entity being aggregated
     * @param repository the entity's repository
     * @param entityManager where the condition's metadata is read
     * @param condition what the list is currently filtered by
     * @param <T> the entity type
     * @return the predicate, or empty when the condition matches every row
     */
    public static <T> Optional<Predicate> of(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root,
                                             Class<T> entityType, JpaSpecificationExecutor<T> repository,
                                             EntityManager entityManager, SearchCondition<?> condition) {
        return of(cb, query, root, entityType, entityManager, condition, null);
    }

    /**
     * @param cb the criteria builder of the aggregate query
     * @param query the aggregate query the predicate is for
     * @param root the aggregate query's root
     * @param entityType the entity being aggregated
     * @param entityManager where the condition's metadata is read
     * @param condition what the list is currently filtered by
     * @param scope a restriction the server adds to the rows, or null
     * @param <T> the entity type
     * @return the predicate, or empty when every row matches
     */
    public static <T> Optional<Predicate> of(CriteriaBuilder cb, CriteriaQuery<?> query, Root<T> root,
                                             Class<T> entityType, EntityManager entityManager,
                                             SearchCondition<?> condition, Specification<T> scope) {
        SearchRequestLimits.validateStructure(condition);
        return Optional.ofNullable(
                new SearchFilter<>(condition, entityManager, entityType, scope).matching(root, query, cb));
    }
}
