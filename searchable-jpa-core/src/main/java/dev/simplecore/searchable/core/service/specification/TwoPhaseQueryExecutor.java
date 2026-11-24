package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.service.join.EntityPaths;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Executes two-phase queries, which cut a page in the database when entities are fetched or sorted
 * through a collection.
 *
 * <p>Phase 1: query the matching IDs only, with conditions, sorting, and pagination.
 * <br>Phase 2: query the full entities using batched IN clauses with fetch joins.
 * <br>The total is taken from Phase 1 when the page is provably the last, otherwise counted.
 *
 * <p>Composite-key entities ({@code @IdClass} / {@code @EmbeddedId}) are delegated to
 * {@link CompositeKeyQueryExecutor}. Single primary-key handling lives here.
 */
public class TwoPhaseQueryExecutor<T> {

    private final SearchFilter<T> filter;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final String primaryKeyField;
    private final CompositeKeyQueryExecutor<T> compositeKeyExecutor;

    public TwoPhaseQueryExecutor(SearchCondition<?> condition,
                                 EntityManager entityManager,
                                 Class<T> entityClass,
                                 JpaSpecificationExecutor<T> specificationExecutor) {
        this(new SearchFilter<>(condition, entityManager, entityClass, null), entityManager, entityClass,
                specificationExecutor);
    }

    TwoPhaseQueryExecutor(SearchFilter<T> filter,
                          EntityManager entityManager,
                          Class<T> entityClass,
                          JpaSpecificationExecutor<T> specificationExecutor) {
        this.filter = filter;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
        this.primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        this.compositeKeyExecutor = SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(primaryKeyField)
                ? new CompositeKeyQueryExecutor<>(filter, entityManager, entityClass, specificationExecutor)
                : null;
    }

    /**
     * Executes the optimized two-phase query with automatic IN clause batching.
     *
     * @param pageRequest the pagination request
     * @param fetchFields the fields to explicitly fetch join in Phase 2
     */
    public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest, Set<String> fetchFields) {
        List<Object> ids = executePhaseOneQuery(pageRequest);

        // A page that is not full is the last one, so its position gives the total. A full page, and
        // an empty page past the first, say nothing about the total, which is then counted.
        long totalCount = ids.size() < pageRequest.getPageSize() && (!ids.isEmpty() || pageRequest.getOffset() == 0)
                ? pageRequest.getOffset() + ids.size() : executeCountQuery();

        // Strip the internal composite-key ordering marker before exposing the pageable to callers.
        PageRequest responsePageRequest = sanitizePageRequest(pageRequest);

        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), responsePageRequest, totalCount);
        }
        return new PageImpl<>(executePhaseTwoQuery(ids, fetchFields), responsePageRequest, totalCount);
    }

    public Optional<T> executeFirst(PageRequest pageRequest, Set<String> fetchFields) {
        List<Object> ids = executePhaseOneQuery(PageRequest.of(0, 1, pageRequest.getSort()));
        return ids.isEmpty() ? Optional.empty() : executePhaseTwoQuery(ids, fetchFields).stream().findFirst();
    }

    /**
     * Phase 1: collect IDs for the requested page.
     */
    private List<Object> executePhaseOneQuery(PageRequest pageRequest) {
        return compositeKeyExecutor != null
                ? compositeKeyExecutor.executePhaseOne(pageRequest)
                : executeRegularPhaseOneQuery(pageRequest);
    }

    /**
     * Phase 1 for single primary-key entities. The filter never multiplies rows, so a plain ID
     * select is one row per entity. Only a sort through a collection joins it, and then the rows are
     * grouped back to one per entity with a deterministic aggregate of each sort value; the
     * conditions are applied to the same joins, so the sort reads the elements they matched.
     */
    private List<Object> executeRegularPhaseOneQuery(PageRequest pageRequest) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        boolean sortThroughCollection = pageRequest.getSort().stream()
                .anyMatch(order -> EntityPaths.traversesCollection(entityManager, entityClass, order.getProperty()));

        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<T> root = query.from(entityClass);
        Path<?> primaryKey = root.get(primaryKeyField);

        List<Selection<?>> selections = new ArrayList<>();
        selections.add(primaryKey);
        List<Order> orders = new ArrayList<>();
        for (Sort.Order sortOrder : pageRequest.getSort()) {
            Expression<?> sortValue;
            if (sortOrder.getProperty().equals(primaryKeyField)) {
                sortValue = primaryKey;
            } else {
                Path<?> sortPath = SpecificationQuerySupport.getPath(root, sortOrder.getProperty());
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

        query.multiselect(selections);
        Predicate matching = sortThroughCollection
                ? filter.matchingJoined(root, query, cb)
                : filter.matching(root, query, cb);
        if (matching != null) {
            query.where(matching);
        }
        if (sortThroughCollection) {
            query.groupBy(primaryKey);
        }
        query.orderBy(orders);

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(Math.toIntExact(pageRequest.getOffset()));
        typedQuery.setMaxResults(pageRequest.getPageSize());
        return typedQuery.getResultList().stream().map(row -> row[0]).collect(Collectors.toList());
    }

    /**
     * The number of matching entities. The filter leaves one row per entity, so a plain count is
     * the count of entities.
     */
    private long executeCountQuery() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> root = countQuery.from(entityClass);
        countQuery.select(cb.count(root));
        Predicate matching = filter.matching(root, countQuery, cb);
        if (matching != null) {
            countQuery.where(matching);
        }
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Phase 2: load entities for the collected IDs using batched IN queries, then restore Phase 1 order.
     */
    private List<T> executePhaseTwoQuery(List<Object> ids, Set<String> fetchFields) {
        int batchSize = idsPerStatement();
        List<T> loaded = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += batchSize) {
            loaded.addAll(loadBatch(ids.subList(i, Math.min(i + batchSize, ids.size())), fetchFields));
        }
        return reorderEntitiesByIds(loaded, ids);
    }

    /**
     * How many identifiers one statement carries. A composite key binds one parameter per key
     * column, so its share is divided to keep a statement within the same number of parameters.
     */
    private int idsPerStatement() {
        int configured = SearchableJpaSettings.positiveInt(entityManager,
                SearchableJpaSettings.IN_CLAUSE_BATCH_SIZE, SearchableJpaSettings.DEFAULT_IN_CLAUSE_BATCH_SIZE);
        return compositeKeyExecutor == null ? configured : Math.max(1, configured / compositeKeyExecutor.keyColumns());
    }

    private List<T> loadBatch(List<Object> ids, Set<String> fetchFields) {
        if (fetchFields == null || fetchFields.isEmpty() || !entityManager.isJoinedToTransaction()) {
            return loadFetchBatch(ids, fetchFields);
        }
        // Independent collections are loaded one after another into the same persistence context,
        // which avoids their Cartesian product (and MultipleBagFetchException).
        Set<String> toOne = new HashSet<>();
        Map<String, Set<String>> collections = new LinkedHashMap<>();
        for (String field : fetchFields) {
            String collection = firstCollectionPath(field);
            if (collection == null) {
                toOne.add(field);
            } else {
                collections.computeIfAbsent(collection, key -> new HashSet<>()).add(field);
            }
        }
        if (collections.size() <= 1) {
            return loadFetchBatch(ids, fetchFields);
        }
        List<T> roots = loadFetchBatch(ids, toOne);
        for (Set<String> paths : collections.values()) {
            loadFetchBatch(ids, paths);
        }
        return roots;
    }

    /** @return the path up to and including the first collection a fetch field runs through, or null */
    private String firstCollectionPath(String field) {
        StringBuilder path = new StringBuilder();
        for (String segment : field.split("\\.")) {
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(segment);
            if (EntityPaths.traversesCollection(entityManager, entityClass, path.toString())) {
                return path.toString();
            }
        }
        return null;
    }

    /**
     * Loads the entities of one batch of identifiers. A fetched collection repeats its owner once
     * per element in the rows, and Hibernate returns each owner once; the statement therefore asks
     * for no {@code DISTINCT}, which a database cannot apply to a row holding a large-object column.
     */
    private List<T> loadFetchBatch(List<Object> ids, Set<String> fetchFields) {
        if (compositeKeyExecutor != null) {
            return compositeKeyExecutor.executePhaseTwo(ids, fetchFields);
        }
        Specification<T> spec = (root, query, cb) -> {
            SpecificationQuerySupport.applyFetchJoins(root, query, fetchFields);
            return root.get(primaryKeyField).in(ids);
        };
        // No ORDER BY: the final order is restored by reorderEntitiesByIds from the Phase 1 IDs.
        return specificationExecutor.findAll(spec, Sort.unsorted());
    }

    /**
     * Reorders entities to match the ID order established in Phase 1.
     */
    private List<T> reorderEntitiesByIds(List<T> entities, List<Object> orderedIds) {
        Map<Object, T> entityMap = new HashMap<>(entities.size());
        for (T entity : entities) {
            entityMap.put(extractEntityId(entity), entity);
        }
        List<T> reordered = new ArrayList<>(orderedIds.size());
        for (Object orderedId : orderedIds) {
            T entity = entityMap.get(orderedId);
            if (entity != null) {
                reordered.add(entity);
            }
        }
        return reordered;
    }

    private Object extractEntityId(T entity) {
        if (compositeKeyExecutor != null) {
            return compositeKeyExecutor.extractId(entity);
        }
        return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    /**
     * Rebuilds the pageable without the internal composite-key ordering marker so it is safe to expose.
     */
    private PageRequest sanitizePageRequest(PageRequest pageRequest) {
        List<Sort.Order> cleaned = pageRequest.getSort().stream()
                .filter(order -> !SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(order.getProperty()))
                .collect(Collectors.toList());
        Sort sort = cleaned.isEmpty() ? Sort.unsorted() : Sort.by(cleaned);
        return PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), sort);
    }
}
