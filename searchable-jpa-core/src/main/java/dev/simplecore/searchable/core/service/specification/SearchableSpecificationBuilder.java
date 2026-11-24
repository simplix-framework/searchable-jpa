package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.join.EntityPaths;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Builds and runs the queries of one search: its filter, its sort and its page.
 * An instance serves one {@link SearchCondition} and is not shared between threads.
 *
 * @param <T> The entity type
 */
public class SearchableSpecificationBuilder<T> {

    private final SearchCondition<?> condition;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final RelationshipAnalyzer<T> relationshipAnalyzer;
    private final SearchFilter<T> filter;
    private final TwoPhaseQueryExecutor<T> twoPhaseQueryExecutor;

    private Set<String> commonToOneFields;

    public SearchableSpecificationBuilder(@NonNull SearchCondition<?> condition,
                                          @NonNull EntityManager entityManager,
                                          @NonNull Class<T> entityClass,
                                          @NonNull JpaSpecificationExecutor<T> specificationExecutor) {
        this(condition, entityManager, entityClass, specificationExecutor, null);
    }

    /**
     * @param condition what the client asked for
     * @param entityManager an entity manager of the entity's persistence unit
     * @param entityClass the entity searched
     * @param specificationExecutor the entity's repository
     * @param scope a restriction the server adds to every query of this search, or null. It is
     *              combined with AND outside the client's conditions and has to return a predicate
     *              over the root alone: no fetch join and no change to the query.
     */
    public SearchableSpecificationBuilder(@NonNull SearchCondition<?> condition,
                                          @NonNull EntityManager entityManager,
                                          @NonNull Class<T> entityClass,
                                          @NonNull JpaSpecificationExecutor<T> specificationExecutor,
                                          Specification<T> scope) {
        SearchRequestLimits.validateStructure(condition);
        this.condition = condition;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
        this.relationshipAnalyzer = new RelationshipAnalyzer<>(entityManager, entityClass);
        this.filter = new SearchFilter<>(condition, entityManager, entityClass, scope);
        this.twoPhaseQueryExecutor = new TwoPhaseQueryExecutor<>(filter, entityManager, entityClass, specificationExecutor);
    }

    public static <T> SearchableSpecificationBuilder<T> of(
            @NonNull SearchCondition<?> condition,
            @NonNull EntityManager entityManager,
            @NonNull Class<T> entityClass,
            @NonNull JpaSpecificationExecutor<T> specificationExecutor) {
        return new SearchableSpecificationBuilder<>(condition, entityManager, entityClass, specificationExecutor);
    }

    public static <T> SearchableSpecificationBuilder<T> of(
            @NonNull SearchCondition<?> condition,
            @NonNull EntityManager entityManager,
            @NonNull Class<T> entityClass,
            @NonNull JpaSpecificationExecutor<T> specificationExecutor,
            Specification<T> scope) {
        return new SearchableSpecificationBuilder<>(condition, entityManager, entityClass, specificationExecutor, scope);
    }

    /**
     * Creates the Sort of the search and appends the primary key, so rows sharing a sort value keep
     * one order across pages. A composite key is appended as a marker the composite-key executor
     * expands into its fields.
     */
    private Sort createSort() {
        SearchCondition.Sort sortCondition = condition.getSort();
        List<Sort.Order> sortOrders = sortCondition == null
                ? new ArrayList<>()
                : sortCondition.getOrders().stream().map(this::createOrder).collect(Collectors.toCollection(ArrayList::new));

        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        if (sortOrders.stream().noneMatch(order -> primaryKeyField.equals(order.getProperty()))) {
            sortOrders.add(Sort.Order.by(primaryKeyField));
        }
        return Sort.by(sortOrders);
    }

    private Sort.Order createOrder(SearchCondition.Order order) {
        String field = order.getEntityField();
        if (field == null || field.isEmpty()) {
            field = order.getField();
        }
        return new Sort.Order(order.isAscending() ? Sort.Direction.ASC : Sort.Direction.DESC, field);
    }

    /**
     * Builds SpecificationWithPageable from SearchCondition.
     *
     * @return SpecificationWithPageable containing specification and page request
     * @deprecated Use buildAndExecuteWithTwoPhaseOptimization() instead for optimized pagination
     */
    @Deprecated
    public SpecificationWithPageable<T> build() {
        return buildSpecificationOnly();
    }

    /**
     * Builds the specification of the search for a repository query that pages for itself or does
     * not page: find one, count, exists.
     *
     * <p>A query that returns the entity fetches its to-one associations with it. The filter never
     * multiplies rows, so the specification asks for no {@code DISTINCT}.
     *
     * @return the specification and the page request of the search
     */
    public SpecificationWithPageable<T> buildSpecificationOnly() {
        Specification<T> specification = (root, query, cb) -> {
            if (SpecificationQuerySupport.selectsEntity(root, query)) {
                SpecificationQuerySupport.applyFetchJoins(root, query, directToOneFields());
            }
            return filter.matching(root, query, cb);
        };
        return new SpecificationWithPageable<>(specification, buildPageRequest());
    }

    /**
     * The filter of the search as a predicate for a query the caller builds: the client's
     * conditions and the server's scope, without any fetch join.
     *
     * @param root the root of the caller's query
     * @param query the caller's query
     * @param cb the criteria builder
     * @return the predicate, or null when every row matches
     */
    public Predicate matchingPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return filter.matching(root, query, cb);
    }

    /** @return the filter of the search as a specification, without any fetch join */
    public Specification<T> matchingSpecification() {
        return filter.asSpecification();
    }

    /** @return whether the search narrows nothing: it has no condition and no scope */
    public boolean isUnfiltered() {
        return filter.isUnfiltered();
    }

    /** @return the sort of the search, ending in the primary key */
    public Sort sort() {
        return createSort();
    }

    /**
     * Runs the search for one page.
     *
     * <p>A single-key entity whose fetches and sort stay off collections is read with one pageable
     * entity query. Fetching or sorting through a collection, and a composite key, use ID paging
     * followed by entity loading, so the page is cut in the database.
     */
    public Page<T> buildAndExecuteWithTwoPhaseOptimization() {
        PageRequest pageRequest = buildPageRequest();
        Set<String> fetchFields = fetchFields();

        boolean compositeKey = SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(
                SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass));
        boolean collectionInFetchOrSort = fetchFields.stream().anyMatch(this::traversesCollection)
                || pageRequest.getSort().stream().anyMatch(order -> traversesCollection(order.getProperty()));
        if (!compositeKey && !collectionInFetchOrSort) {
            Specification<T> direct = (root, query, cb) -> {
                SpecificationQuerySupport.applyFetchJoins(root, query, fetchFields);
                return filter.matching(root, query, cb);
            };
            return specificationExecutor.findAll(direct, pageRequest);
        }
        return twoPhaseQueryExecutor.executeWithTwoPhaseOptimization(pageRequest, fetchFields);
    }

    /**
     * Runs the search for one window of a scroll.
     *
     * <p>The window is cut by one statement over the entity, sorted by the search's sort and then
     * the primary key. A keyset position continues after the last row of the previous window by
     * comparing those sort values, so reaching a late window costs the same as the first. A sort
     * value that is missing counts as the smallest value, which the sort requests explicitly.
     *
     * @param position where the window starts: {@code ScrollPosition.keyset()} or an offset for the
     *                 first window, afterwards a position of the previous window
     * @return the window
     * @throws SearchableValidationException when the sort runs through a collection, or a keyset
     *         position does not carry the values of this search's sort
     * @throws SearchableOperationException when a keyset position is used for an entity whose
     *         identifier is embedded
     */
    public Window<T> scroll(ScrollPosition position) {
        Sort sort = plainSort(true);
        int size = buildPageRequest().getPageSize();
        if (position instanceof KeysetScrollPosition keyset) {
            if (SearchableFieldUtils.entityKey(entityManager, entityClass).embedded()) {
                throw new SearchableOperationException(MessageUtils.getMessage("scroll.embedded.id.unsupported",
                        new Object[]{entityClass.getSimpleName()}));
            }
            Set<String> sorted = sort.stream().map(Sort.Order::getProperty).collect(Collectors.toSet());
            if (!keyset.isInitial() && !keyset.getKeys().keySet().equals(sorted)) {
                throw new SearchableValidationException(MessageUtils.getMessage("scroll.position.invalid"));
            }
        }
        Specification<T> specification = (root, query, cb) -> {
            SpecificationQuerySupport.applyFetchJoins(root, query, directToOneFields());
            return filter.matching(root, query, cb);
        };
        return specificationExecutor.<T, Window<T>>findBy(specification,
                found -> found.sortBy(sort).limit(size).scroll(position));
    }

    /**
     * Runs the search for one page of a projection, selecting only what the projection reads.
     *
     * <p>An interface whose getters name attributes of the entity, or a record whose components do,
     * is read with a statement that selects those columns. A getter that returns another projection
     * loads the associated entity whole.
     *
     * @param projection the interface or record to return
     * @param <P> its type
     * @return the page
     * @throws SearchableValidationException when the sort runs through a collection
     */
    public <P> Page<P> project(Class<P> projection) {
        PageRequest requested = buildPageRequest();
        PageRequest pageRequest = PageRequest.of(requested.getPageNumber(), requested.getPageSize(), plainSort(false));
        return specificationExecutor.<T, Page<P>>findBy(filter.asSpecification(),
                found -> found.as(projection).page(pageRequest));
    }

    /**
     * The sort of the search for a statement over the entity alone: its orders, then the parts of
     * the primary key not already among them.
     *
     * @param missingValuesFirst whether a missing value is ordered as the smallest value instead of
     *                           wherever the database puts it
     */
    private Sort plainSort(boolean missingValuesFirst) {
        List<Sort.Order> orders = new ArrayList<>();
        SearchCondition.Sort sortCondition = condition.getSort();
        if (sortCondition != null) {
            for (SearchCondition.Order requested : sortCondition.getOrders()) {
                Sort.Order order = createOrder(requested);
                if (traversesCollection(order.getProperty())) {
                    throw new SearchableValidationException(MessageUtils.getMessage("sort.collection.unsupported",
                            new Object[]{requested.getField()}));
                }
                if (missingValuesFirst) {
                    order = order.isAscending() ? order.nullsFirst() : order.nullsLast();
                }
                orders.add(order);
            }
        }
        for (String keyPath : SearchableFieldUtils.entityKey(entityManager, entityClass).paths()) {
            if (orders.stream().noneMatch(order -> order.getProperty().equals(keyPath))) {
                orders.add(Sort.Order.asc(keyPath));
            }
        }
        return Sort.by(orders);
    }

    /** Fetches at most one root entity and does not issue a total-count query. */
    public Optional<T> buildAndExecuteFirst() {
        return twoPhaseQueryExecutor.executeFirst(buildPageRequest(), fetchFields());
    }

    private boolean traversesCollection(String path) {
        return !SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(path)
                && EntityPaths.traversesCollection(entityManager, entityClass, path);
    }

    /** The fetch fields the condition names together with the associations fetched with every list. */
    private Set<String> fetchFields() {
        Set<String> fields = new HashSet<>(commonToOneFields());
        if (condition.getFetchFields() != null) {
            fields.addAll(condition.getFetchFields());
        }
        return fields;
    }

    private Set<String> commonToOneFields() {
        if (commonToOneFields == null) {
            commonToOneFields = relationshipAnalyzer.detectCommonToOneFields();
        }
        return commonToOneFields;
    }

    /** The to-one associations of the entity itself, which a single entity query can fetch. */
    private Set<String> directToOneFields() {
        return commonToOneFields().stream().filter(field -> !field.contains(".")).collect(Collectors.toSet());
    }

    private PageRequest buildPageRequest() {
        Integer pageNum = condition.getPage();
        Integer sizeNum = condition.getSize();

        int page = pageNum != null ? pageNum : 0;
        int size = sizeNum != null ? sizeNum : SearchRequestLimits.defaultPageSize(entityManager);
        SearchRequestLimits.validatePage(entityManager, page, size);

        return PageRequest.of(page, size, createSort());
    }
}
