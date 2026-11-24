package dev.simplecore.searchable.core.service.specification;

import jakarta.persistence.EntityManager;
import org.hibernate.dialect.AbstractTransactSQLDialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * What the query code has to know about the database behind an entity manager.
 */
final class DatabaseTraits {

    private DatabaseTraits() {
    }

    /**
     * @param entityManager an entity manager of the persistence unit being queried
     * @return whether a LIKE pattern reads {@code [} as the start of a character class
     */
    static boolean bracketOpensCharacterClass(EntityManager entityManager) {
        return bracketOpensCharacterClass(entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect());
    }

    /**
     * Transact-SQL (SQL Server, Sybase) gives brackets a meaning in LIKE patterns; the other
     * databases treat a bracket as an ordinary character.
     */
    static boolean bracketOpensCharacterClass(Dialect dialect) {
        return dialect instanceof AbstractTransactSQLDialect;
    }
}
