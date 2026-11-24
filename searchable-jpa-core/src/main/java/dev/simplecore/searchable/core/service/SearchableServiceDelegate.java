package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.core.service.specification.SpecificationWithPageable;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;

/**
 * Standalone delegate that encapsulates all SearchableService implementation logic.
 * <p>
 * This class can be used in two ways:
 * <ul>
 *   <li>Directly, by creating an instance and calling methods on it</li>
 *   <li>Via {@link SearchableServiceSupport} interface, which provides default methods
 *       that delegate to an instance of this class</li>
 * </ul>
 *
 * @param <T>  entity type
 * @param <ID> entity ID type
 * @see SearchableServiceSupport
 * @see DefaultSearchableService
 */
public class SearchableServiceDelegate<T, ID> implements SearchableService<T> {

    private static final ModelMapper MODEL_MAPPER;
    static {
        MODEL_MAPPER = new ModelMapper();
        MODEL_MAPPER.getConfiguration()
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE)
                .setPropertyCondition(ctx -> ctx.getSource() != null)
                .setMatchingStrategy(MatchingStrategies.STRICT);
    }

    private final JpaRepository<T, ID> repository;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final Class<T> entityClass;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

    public SearchableServiceDelegate(JpaRepository<T, ID> repository, EntityManager entityManager, Class<T> entityClass) {
        this(repository, entityManager, entityClass, null);
    }

    /**
     * @param repository the entity's repository, which also implements {@link JpaSpecificationExecutor}
     * @param entityManager an entity manager of the entity's persistence unit
     * @param entityClass the entity
     * @param transactionManager the application's transaction manager, or null. With one, every
     *                           operation runs in a transaction it manages: the caller's when one is
     *                           active, a new one otherwise. Without one, an operation outside a
     *                           transaction the entity manager has joined runs in a JPA transaction
     *                           of its own.
     */
    @SuppressWarnings("unchecked")
    public SearchableServiceDelegate(JpaRepository<T, ID> repository, EntityManager entityManager, Class<T> entityClass,
                                     PlatformTransactionManager transactionManager) {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        if (!(repository instanceof JpaSpecificationExecutor<?>)) {
            throw new SearchableConfigurationException(MessageUtils.getMessage("service.repository.specification.required"));
        }
        this.repository = repository;
        this.specificationExecutor = (JpaSpecificationExecutor<T>) repository;
        this.entityClass = entityClass;
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
    }

    public SpecificationWithPageable<T> createSpecification(SearchCondition<?> searchCondition) {
        return createSpecificationBuilder(searchCondition, null).buildSpecificationOnly();
    }

    public SearchableSpecificationBuilder<T> createSpecificationBuilder(SearchCondition<?> searchCondition) {
        return createSpecificationBuilder(searchCondition, null);
    }

    /**
     * @param searchCondition what the client asked for
     * @param scope a restriction the server adds, or null; see
     *              {@link SearchableServiceSupport#findAllWithSearch(SearchCondition, Specification)}
     * @return the builder of the search's queries
     */
    public SearchableSpecificationBuilder<T> createSpecificationBuilder(SearchCondition<?> searchCondition,
                                                                        Specification<T> scope) {
        return SearchableSpecificationBuilder.of(searchCondition, entityManager, entityClass, specificationExecutor, scope);
    }

    @Override
    @NonNull
    public Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return findAllWithSearch(searchCondition, (Specification<T>) null);
    }

    @NonNull
    public Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition, scope);
        return readInTransaction(builder::buildAndExecuteWithTwoPhaseOptimization);
    }

    @Override
    @NonNull
    public <P> Page<P> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<P> projectionClass) {
        return findAllWithSearch(searchCondition, null, projectionClass);
    }

    @NonNull
    public <P> Page<P> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                         Class<P> projectionClass) {
        if (!projectionClass.isInterface()) {
            throw new SearchableConfigurationException(MessageUtils.getMessage("service.projection.interface.required"));
        }
        return findAllWithSearch(searchCondition, scope)
                .map(entity -> projectionFactory.createProjection(projectionClass, entity));
    }

    @NonNull
    public <P> Page<P> projectWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                         Class<P> projection) {
        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition, scope);
        return readInTransaction(() -> builder.project(projection));
    }

    @NonNull
    public Window<T> scrollWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                      @NonNull ScrollPosition position) {
        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition, scope);
        return readInTransaction(() -> builder.scroll(position));
    }

    /**
     * @param cursor text written by {@link SearchCursor#encode}, or null or blank for the first window
     * @return the scroll position the text names
     */
    @NonNull
    public ScrollPosition scrollPosition(String cursor) {
        return SearchCursor.decode(cursor, entityManager, entityClass);
    }

    @Override
    @NonNull
    public Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return findOneWithSearch(searchCondition, null);
    }

    @NonNull
    public Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return specificationExecutor.findOne(
                createSpecificationBuilder(searchCondition, scope).buildSpecificationOnly().getSpecification());
    }

    @Override
    @NonNull
    public Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return findFirstWithSearch(searchCondition, null);
    }

    @NonNull
    public Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        SearchCondition<?> firstCondition = new SearchCondition<>(searchCondition.getNodes(), 0, 1);
        firstCondition.setSort(searchCondition.getSort());
        firstCondition.setFetchFields(searchCondition.getFetchFields());
        SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(firstCondition, scope);
        return readInTransaction(builder::buildAndExecuteFirst);
    }

    @Override
    public long deleteWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return deleteWithSearch(searchCondition, null);
    }

    public long deleteWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return writeInTransaction(owned -> new WriteQueryExecutor<>(entityManager, entityClass, specificationExecutor, scope)
                .execute(searchCondition, repository::delete, owned));
    }

    @Override
    public long countWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return countWithSearch(searchCondition, null);
    }

    public long countWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return specificationExecutor.count(createSpecificationBuilder(searchCondition, scope).matchingSpecification());
    }

    @Override
    public boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition) {
        return existsWithSearch(searchCondition, null);
    }

    public boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope) {
        return specificationExecutor.exists(createSpecificationBuilder(searchCondition, scope).matchingSpecification());
    }

    @Override
    public long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData) {
        return updateWithSearch(searchCondition, null, updateData);
    }

    public long updateWithSearch(@NonNull SearchCondition<?> searchCondition, Specification<T> scope,
                                 @NonNull Object updateData) {
        // Retain ModelMapper conversion, but copy only properties present in the original
        // payload. Mapping a narrow DTO must not reset unrelated primitive entity fields.
        Object mappedData = entityClass.isInstance(updateData) ? updateData : MODEL_MAPPER.map(updateData, entityClass);
        return writeInTransaction(owned -> new WriteQueryExecutor<>(entityManager, entityClass, specificationExecutor, scope)
                .execute(searchCondition, entity -> copyNonNullProperties(mappedData, updateData, entity), owned));
    }

    private <R> R readInTransaction(Supplier<R> work) {
        if (transactionManager != null) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setReadOnly(true);
            return transaction.execute(status -> work.get());
        }
        if (entityManager.isJoinedToTransaction()) {
            return work.get();
        }
        TransactionTemplate transaction = new TransactionTemplate(new JpaTransactionManager(entityManager.getEntityManagerFactory()));
        transaction.setReadOnly(true);
        return transaction.execute(status -> work.get());
    }

    /**
     * @param work the write, told whether the transaction it runs in was begun for it. Only then are
     *             the entities it loaded its own to release.
     */
    private long writeInTransaction(Function<Boolean, Long> work) {
        Long result;
        if (transactionManager != null) {
            result = new TransactionTemplate(transactionManager).execute(status -> work.apply(status.isNewTransaction()));
        } else if (entityManager.isJoinedToTransaction()) {
            result = work.apply(false);
        } else {
            result = new TransactionTemplate(new JpaTransactionManager(entityManager.getEntityManagerFactory()))
                    .execute(status -> work.apply(true));
        }
        return result == null ? 0 : result;
    }

    /**
     * Copies supplied, non-null mutable properties through the entity hierarchy.
     * IDs, versions, audit-managed fields, non-updatable fields and collections are excluded.
     */
    private void copyNonNullProperties(Object source, Object payload, T target) {
        for (Class<?> current = entityClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (isProtectedField(field) || !isProvided(payload, field.getName())) continue;
                if (Collection.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(source);
                    if (value != null) {
                        field.set(target, value);
                    }
                } catch (IllegalAccessException e) {
                    throw new SearchableOperationException(
                            MessageUtils.getMessage("service.update.copy.failed", new Object[]{field.getName()}), e);
                }
            }
        }
    }
    private boolean isProvided(Object payload, String name) {
        if (payload instanceof Map<?, ?> map) return map.containsKey(name) && map.get(name) != null;
        var field = SearchableFieldUtils.findFieldInHierarchy(payload.getClass(), name);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            return ReflectionUtils.getField(field, payload) != null;
        }
        var wrapper = new BeanWrapperImpl(payload);
        return wrapper.isReadableProperty(name) && wrapper.getPropertyValue(name) != null;
    }

    private boolean isProtectedField(Field field) {
        if (Modifier.isFinal(field.getModifiers())) return true;
        try {
            var attribute = entityManager.getMetamodel().entity(entityClass).getAttribute(field.getName());
            if (attribute instanceof SingularAttribute<?, ?> singular &&
                    (singular.isId() || singular.isVersion())) return true;
            if (protectedMember(attribute.getJavaMember())) return true;
        } catch (IllegalArgumentException e) {
            return true; // Not a persistent field.
        }
        return protectedMember(field);
    }

    private boolean protectedMember(Member member) {
        if (!(member instanceof AnnotatedElement element)) return false;
        Column column = element.getAnnotation(Column.class);
        if (column != null && !column.updatable()) return true;
        return element.isAnnotationPresent(Id.class)
                || element.isAnnotationPresent(EmbeddedId.class)
                || element.isAnnotationPresent(Version.class)
                || element.isAnnotationPresent(CreatedDate.class)
                || element.isAnnotationPresent(CreatedBy.class)
                || element.isAnnotationPresent(LastModifiedDate.class)
                || element.isAnnotationPresent(LastModifiedBy.class)
                || element.isAnnotationPresent(CreationTimestamp.class)
                || element.isAnnotationPresent(UpdateTimestamp.class);
    }
}
