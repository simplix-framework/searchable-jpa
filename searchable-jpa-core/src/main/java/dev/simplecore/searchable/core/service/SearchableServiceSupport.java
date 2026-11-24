package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.util.Optional;

/**
 * Mixin interface that provides {@link SearchableService} implementation via delegation.
 * <p>
 * Classes that cannot extend {@link DefaultSearchableService} (e.g., because they already
 * extend another class) can implement this interface instead. All {@link SearchableService}
 * methods are provided as default methods that delegate to a {@link SearchableServiceDelegate}.
 * <p>
 * Usage:
 * <pre>{@code
 * public class MyService extends SomeOtherBaseClass
 *         implements SearchableServiceSupport<MyEntity, Long> {
 *
 *     private final SearchableServiceDelegate<MyEntity, Long> searchableDelegate;
 *
 *     public MyService(JpaRepository<MyEntity, Long> repository, EntityManager em) {
 *         this.searchableDelegate = new SearchableServiceDelegate<>(repository, em, MyEntity.class);
 *     }
 *
 *     @Override
 *     public SearchableServiceDelegate<MyEntity, Long> getSearchableDelegate() {
 *         return searchableDelegate;
 *     }
 * }
 * }</pre>
 *
 * @param <T>  entity type
 * @param <ID> entity ID type
 * @see SearchableServiceDelegate
 * @see DefaultSearchableService
 */
public interface SearchableServiceSupport<T, ID> extends SearchableService<T> {

    /**
     * Returns the delegate that provides the actual SearchableService implementation.
     *
     * @return the searchable service delegate
     */
    SearchableServiceDelegate<T, ID> getSearchableDelegate();

    @Override
    @NonNull
    default Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().findAllWithSearch(searchCondition);
    }

    @Override
    @NonNull
    default <P> Page<P> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<P> projectionClass) {
        return getSearchableDelegate().findAllWithSearch(searchCondition, projectionClass);
    }

    @Override
    @NonNull
    default Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().findOneWithSearch(searchCondition);
    }

    @Override
    @NonNull
    default Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().findFirstWithSearch(searchCondition);
    }

    @Override
    default long deleteWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().deleteWithSearch(searchCondition);
    }

    @Override
    default long countWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().countWithSearch(searchCondition);
    }

    @Override
    default boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return getSearchableDelegate().existsWithSearch(searchCondition);
    }

    @Override
    default long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData) {
        return getSearchableDelegate().updateWithSearch(searchCondition, updateData);
    }

    /**
     * Search entities within a scope the server adds.
     *
     * <p>The scope is combined with AND outside whatever the client sent, so no condition of the
     * client can widen it: a tenant, an owner, a soft-delete flag. It is applied to the entity row
     * itself and must not join a collection or add a fetch join; a restriction on a collection is
     * written as a subquery.
     *
     * @param searchCondition what the client asked for
     * @param scope the restriction every returned row satisfies, or null for none
     * @return page of matching entities inside the scope
     */
    @NonNull
    default Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return getSearchableDelegate().findAllWithSearch(searchCondition, scope);
    }

    /**
     * Search entities within a server scope and map each result to a Spring Data projection.
     *
     * @see #findAllWithSearch(SearchCondition, Specification)
     */
    @NonNull
    default <P> Page<P> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                          Class<P> projectionClass) {
        return getSearchableDelegate().findAllWithSearch(searchCondition, scope, projectionClass);
    }

    /** @see #findAllWithSearch(SearchCondition, Specification) */
    @NonNull
    default Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return getSearchableDelegate().findOneWithSearch(searchCondition, scope);
    }

    /** @see #findAllWithSearch(SearchCondition, Specification) */
    @NonNull
    default Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return getSearchableDelegate().findFirstWithSearch(searchCondition, scope);
    }

    /** @see #findAllWithSearch(SearchCondition, Specification) */
    default long countWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return getSearchableDelegate().countWithSearch(searchCondition, scope);
    }

    /** @see #findAllWithSearch(SearchCondition, Specification) */
    default boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return getSearchableDelegate().existsWithSearch(searchCondition, scope);
    }

    /**
     * Delete the entities matching the search conditions inside a server scope.
     *
     * @see #findAllWithSearch(SearchCondition, Specification)
     */
    default long deleteWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return getSearchableDelegate().deleteWithSearch(searchCondition, scope);
    }

    /**
     * Update the entities matching the search conditions inside a server scope.
     *
     * @see #findAllWithSearch(SearchCondition, Specification)
     */
    default long updateWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                  @NonNull Object updateData) {
        return getSearchableDelegate().updateWithSearch(searchCondition, scope, updateData);
    }

    /**
     * Search one window of a scroll.
     *
     * <p>A scroll returns no total and continues from a position instead of a page number. With a
     * keyset position the next window starts after the last row of the previous one by comparing
     * sort values, so a late window costs what the first one does. The sort must not run through a
     * collection, and an entity whose identifier is embedded scrolls by offset only.
     *
     * @param searchCondition the conditions, the sort and the window size; its page number is not read
     * @param position {@code ScrollPosition.keyset()} for the first window, then
     *                 {@code window.positionAt(window.size() - 1)} of the previous one
     * @return the window, which knows whether another follows
     */
    @NonNull
    default Window<T> scrollWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull ScrollPosition position) {
        return getSearchableDelegate().scrollWithSearch(searchCondition, null, position);
    }

    /**
     * Search one window of a scroll inside a server scope.
     *
     * @see #scrollWithSearch(SearchCondition, ScrollPosition)
     * @see #findAllWithSearch(SearchCondition, Specification)
     */
    @NonNull
    default Window<T> scrollWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                       @NonNull ScrollPosition position) {
        return getSearchableDelegate().scrollWithSearch(searchCondition, scope, position);
    }

    /**
     * Reads a scroll position a client handed back.
     *
     * @param cursor text written by {@link SearchCursor#encode}, or null or blank for the first window
     * @return the position
     */
    @NonNull
    default ScrollPosition scrollPosition(String cursor) {
        return getSearchableDelegate().scrollPosition(cursor);
    }

    /**
     * Search and return a projection, selecting only the columns the projection reads.
     *
     * <p>Unlike {@link #findAllWithSearch(SearchCondition, Class)}, which loads the entities and
     * their associations and then presents them through the projection, this reads the projection
     * straight from the database. It suits a list that shows a few columns of a wide entity. The
     * projection is an interface whose getters name attributes of the entity, or a record whose
     * components do; the sort must not run through a collection.
     *
     * @param searchCondition search conditions
     * @param projection the interface or record to return
     * @param <P> its type
     * @return page of projections
     */
    @NonNull
    default <P> Page<P> projectWithSearch(@NonNull SearchCondition<?> searchCondition, Class<P> projection) {
        return getSearchableDelegate().projectWithSearch(searchCondition, null, projection);
    }

    /**
     * Search and return a column-selecting projection inside a server scope.
     *
     * @see #projectWithSearch(SearchCondition, Class)
     * @see #findAllWithSearch(SearchCondition, Specification)
     */
    @NonNull
    default <P> Page<P> projectWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                          Class<P> projection) {
        return getSearchableDelegate().projectWithSearch(searchCondition, scope, projection);
    }
}
