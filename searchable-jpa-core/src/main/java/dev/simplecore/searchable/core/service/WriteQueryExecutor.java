package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.utils.EntityKey;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Visits matching rows in primary-key order, without OFFSET skips when writes change the filter. */
final class WriteQueryExecutor<T> {
    private final EntityManager em;
    private final Class<T> type;
    private final JpaSpecificationExecutor<T> repository;
    private final Specification<T> scope;

    WriteQueryExecutor(EntityManager em, Class<T> type, JpaSpecificationExecutor<T> repository,
                       Specification<T> scope) {
        this.em = em;
        this.type = type;
        this.repository = repository;
        this.scope = scope;
    }

    /**
     * @param condition which rows to visit
     * @param action what to do with each
     * @param releaseEntities whether a visited chunk is detached once it is flushed
     * @return how many rows were visited
     * @throws SearchableValidationException when nothing narrows the rows and the persistence unit
     *         requires a search-based write to be filtered
     */
    long execute(SearchCondition<?> condition, Consumer<T> action, boolean releaseEntities) {
        SearchableSpecificationBuilder<T> search = SearchableSpecificationBuilder.of(condition, em, type, repository, scope);
        if (search.isUnfiltered() && SearchableJpaSettings.flag(em, SearchableJpaSettings.REQUIRE_WRITE_FILTER, false)) {
            throw new SearchableValidationException(MessageUtils.getMessage("write.filter.required"));
        }
        int chunkSize = SearchableJpaSettings.positiveInt(em,
                SearchableJpaSettings.WRITE_CHUNK_SIZE, SearchableJpaSettings.DEFAULT_WRITE_CHUNK_SIZE);
        EntityKey key = SearchableFieldUtils.entityKey(em, type);
        List<String> keyPaths = key.paths();
        List<Object> cursor = null;
        long count = 0;
        while (true) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(type);
            Root<T> root = query.from(type);
            List<Predicate> where = new ArrayList<>();
            // The filter alone, without entity fetch joins, so the chunk is always cut in SQL.
            Predicate matching = search.matchingPredicate(root, query, cb);
            if (matching != null) {
                where.add(matching);
            }
            if (cursor != null) {
                where.add(after(cb, root, keyPaths, cursor));
            }
            query.select(root).where(where.toArray(new Predicate[0]));
            query.orderBy(keyPaths.stream().map(path -> cb.asc(path(root, path))).toList());
            List<T> batch = em.createQuery(query).setMaxResults(chunkSize).getResultList();
            if (batch.isEmpty()) {
                return count;
            }
            boolean lastChunk = batch.size() < chunkSize;
            // Capture the last key before callbacks mutate or remove the entities.
            cursor = key.values(em, type, batch.get(batch.size() - 1));
            for (T entity : batch) {
                action.accept(entity);
                count++;
            }
            em.flush();
            // Never detach objects owned by a caller's pre-existing transaction.
            if (releaseEntities) {
                batch.forEach(em::detach);
            }
            // A chunk that is not full is the last one.
            if (lastChunk) {
                return count;
            }
        }
    }

    /** The rows after a key, comparing the key's parts in order. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate after(CriteriaBuilder cb, Root<T> root, List<String> keyPaths, List<Object> cursor) {
        List<Predicate> alternatives = new ArrayList<>();
        for (int i = 0; i < keyPaths.size(); i++) {
            List<Predicate> prefix = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                prefix.add(cb.equal(path(root, keyPaths.get(j)), cursor.get(j)));
            }
            prefix.add(cb.greaterThan((Expression) path(root, keyPaths.get(i)), (Comparable) cursor.get(i)));
            alternatives.add(cb.and(prefix.toArray(new Predicate[0])));
        }
        return cb.or(alternatives.toArray(new Predicate[0]));
    }

    private Path<?> path(Path<?> root, String name) {
        Path<?> path = root;
        for (String part : name.split("\\.")) {
            path = path.get(part);
        }
        return path;
    }
}
