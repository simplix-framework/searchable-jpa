package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.repository.TestUpdatableEntityRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A delegate given the application's transaction manager runs inside the caller's transaction when
 * there is one and inside its own otherwise. The rows are committed by the test's own setup, so what
 * a rolled-back caller leaves behind is read from the database, not from a session.
 */
@SpringBootTest
@ContextConfiguration(classes = BaseTestConfig.class)
class TransactionOwnershipTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private TestUpdatableEntityRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final String batch = "tx-" + UUID.randomUUID();
    private SearchableServiceDelegate<TestUpdatableEntity, Long> service;

    @BeforeEach
    void commitRows() {
        service = new SearchableServiceDelegate<>(repository, em, TestUpdatableEntity.class, transactionManager);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) {
                TestUpdatableEntity row = new TestUpdatableEntity();
                row.setName(batch);
                row.setStatus("NEW");
                em.persist(row);
            }
        });
    }

    @AfterEach
    void removeRows() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                em.createQuery("delete from TestUpdatableEntity e where e.name = :name").setParameter("name", batch).executeUpdate());
    }

    private SearchCondition<TestUpdatableEntity> ownRows() {
        SearchCondition<TestUpdatableEntity> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition(null, "name", SearchOperator.EQUALS, batch, null, "name"));
        return condition;
    }

    private List<String> statusesInTheDatabase() {
        return new TransactionTemplate(transactionManager).execute(status ->
                em.createQuery("select e.status from TestUpdatableEntity e where e.name = :name", String.class)
                        .setParameter("name", batch).getResultList());
    }

    @Test
    @DisplayName("outside a transaction a write begins one of its own and commits it")
    void outsideATransactionTheWriteCommits() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        assertThat(service.updateWithSearch(ownRows(), Map.of("status", "DONE"))).isEqualTo(3);

        assertThat(statusesInTheDatabase()).containsOnly("DONE");
    }

    @Test
    @DisplayName("inside a caller's transaction a write shares its fate and leaves its entities managed")
    void insideATransactionTheWriteBelongsToTheCaller() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TestUpdatableEntity held = em.createQuery("select e from TestUpdatableEntity e where e.name = :name", TestUpdatableEntity.class)
                    .setParameter("name", batch).setMaxResults(1).getSingleResult();

            assertThat(service.updateWithSearch(ownRows(), Map.of("status", "DONE"))).isEqualTo(3);

            assertThat(em.contains(held)).as("the caller's entity stays managed").isTrue();
            assertThat(held.getStatus()).isEqualTo("DONE");
            status.setRollbackOnly();
        });

        assertThat(statusesInTheDatabase()).containsOnly("NEW");
    }

    @Test
    @DisplayName("outside a transaction a read with a collection fetch runs in one transaction of its own")
    void outsideATransactionAReadRuns() {
        assertThat(service.findAllWithSearch(ownRows()).getContent()).hasSize(3);
        assertThat(service.scrollWithSearch(ownRows(), null, service.scrollPosition(null)).getContent()).hasSize(3);
    }
}
