package dev.simplecore.searchable.test.support;

import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.core.service.specification.RelationshipAnalyzer;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How many statements a page of a search may send.
 *
 * <p>A statement count does not depend on how busy the machine is, and it is what grows when a
 * search starts loading row by row. One statement pages the identifiers and one counts; each batch
 * of identifiers is then loaded by one statement, or by one for the entities and one per collection
 * when more than one collection is fetched with them.
 */
public final class StatementBudget {

    private StatementBudget() {
    }

    /**
     * @param entityManager an entity manager of the entity's persistence unit
     * @param entityClass the entity searched
     * @param pageSize the size of the page asked for
     * @return the most statements one page may send
     */
    public static int ofPage(EntityManager entityManager, Class<?> entityClass, int pageSize) {
        Set<String> fetchedCollections = new RelationshipAnalyzer<>(entityManager, entityClass)
                .detectNestedToOneRelationships().stream()
                .map(path -> path.substring(0, path.indexOf('.')))
                .collect(Collectors.toSet());
        int statementsPerBatch = fetchedCollections.size() <= 1 ? 1 : 1 + fetchedCollections.size();
        int idsPerStatement = SearchableJpaSettings.positiveInt(entityManager,
                SearchableJpaSettings.IN_CLAUSE_BATCH_SIZE, SearchableJpaSettings.DEFAULT_IN_CLAUSE_BATCH_SIZE);
        int batches = (int) Math.ceil(pageSize / (double) idsPerStatement);
        return 2 + batches * statementsPerBatch;
    }
}
