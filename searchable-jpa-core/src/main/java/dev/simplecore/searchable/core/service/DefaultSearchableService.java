package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.service.specification.SpecificationWithPageable;
import jakarta.persistence.EntityManager;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Default implementation of {@link SearchableService} that can be extended directly.
 * <p>
 * This class delegates all SearchableService operations to a {@link SearchableServiceDelegate}.
 * Existing subclasses continue to work without modification.
 * <p>
 * For classes that need to extend a different base class, use {@link SearchableServiceSupport}
 * interface with {@link SearchableServiceDelegate} instead.
 *
 * @param <T>  entity type
 * @param <ID> entity ID type
 * @see SearchableServiceSupport
 * @see SearchableServiceDelegate
 */
@NoRepositoryBean
public class DefaultSearchableService<T, ID> implements SearchableServiceSupport<T, ID> {

    protected final JpaRepository<T, ID> repository;
    private final SearchableServiceDelegate<T, ID> delegate;

    public DefaultSearchableService(JpaRepository<T, ID> repository, EntityManager entityManager) {
        this(repository, entityManager, null);
    }

    /**
     * @param repository the entity's repository
     * @param entityManager an entity manager of the entity's persistence unit
     * @param transactionManager the application's transaction manager, or null to let an operation
     *                           outside a transaction begin a JPA transaction of its own
     */
    @SuppressWarnings("unchecked")
    public DefaultSearchableService(JpaRepository<T, ID> repository, EntityManager entityManager,
                                    PlatformTransactionManager transactionManager) {
        // Resolve the entity type by walking the class hierarchy, so subclasses that extend through
        // an intermediate (possibly abstract) class are supported and do not fail with a ClassCastException.
        Class<?>[] typeArguments = GenericTypeResolver.resolveTypeArguments(getClass(), DefaultSearchableService.class);
        if (typeArguments == null || typeArguments.length < 1 || typeArguments[0] == null) {
            throw new SearchableConfigurationException(
                    MessageUtils.getMessage("service.entity.type.unresolved", new Object[]{getClass().getName()}));
        }
        Class<T> entityClass = (Class<T>) typeArguments[0];
        this.repository = repository;
        this.delegate = new SearchableServiceDelegate<>(repository, entityManager, entityClass, transactionManager);
    }

    @Override
    public SearchableServiceDelegate<T, ID> getSearchableDelegate() {
        return delegate;
    }

    protected SpecificationWithPageable<T> createSpecification(SearchCondition<?> searchCondition) {
        return delegate.createSpecification(searchCondition);
    }

    protected SearchableSpecificationBuilder<T> createSpecificationBuilder(SearchCondition<?> searchCondition) {
        return delegate.createSpecificationBuilder(searchCondition);
    }
}
