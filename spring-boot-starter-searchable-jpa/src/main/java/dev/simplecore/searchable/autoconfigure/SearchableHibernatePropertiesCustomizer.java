package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.core.config.SearchableJpaSettings;
import dev.simplecore.searchable.properties.SearchableProperties;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

/**
 * Hands the {@code searchable.*} settings to the EntityManagerFactory Spring Boot builds.
 *
 * <p>Spring Boot applies a customizer while it assembles the factory's properties, after the
 * application's own {@code spring.jpa.properties} are in the map. Every value is therefore added
 * only where the application set none, which keeps the library's Hibernate values defaults.
 */
public class SearchableHibernatePropertiesCustomizer implements HibernatePropertiesCustomizer {

    private final SearchableProperties properties;

    public SearchableHibernatePropertiesCustomizer(SearchableProperties properties) {
        this.properties = properties;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        SearchableProperties.HibernateProperties hibernate = properties.getHibernate();

        hibernateProperties.putIfAbsent(SearchableJpaSettings.MAX_PAGE_SIZE,
                String.valueOf(properties.getMaxPageSize()));
        hibernateProperties.putIfAbsent(SearchableJpaSettings.DEFAULT_PAGE_SIZE,
                String.valueOf(properties.getDefaultPageSize()));
        hibernateProperties.putIfAbsent(SearchableJpaSettings.AUTO_FETCH_COLLECTIONS,
                String.valueOf(hibernate.isAutoFetchCollections()));
        hibernateProperties.putIfAbsent(SearchableJpaSettings.IN_CLAUSE_BATCH_SIZE,
                String.valueOf(properties.getInClauseBatchSize()));
        hibernateProperties.putIfAbsent(SearchableJpaSettings.WRITE_CHUNK_SIZE,
                String.valueOf(properties.getWrite().getChunkSize()));
        hibernateProperties.putIfAbsent(SearchableJpaSettings.REQUIRE_WRITE_FILTER,
                String.valueOf(properties.getWrite().isRequireFilter()));
        hibernateProperties.putIfAbsent(SearchableJpaSettings.MAX_AGGREGATE_GROUPS,
                String.valueOf(properties.getAggregate().getMaxGroups()));
        hibernateProperties.putIfAbsent(SearchableJpaSettings.MAX_BUCKETS,
                String.valueOf(properties.getBucket().getMaxBuckets()));

        if (!hibernate.isAutoOptimization()) {
            return;
        }
        hibernateProperties.putIfAbsent(AvailableSettings.DEFAULT_BATCH_FETCH_SIZE,
                String.valueOf(hibernate.getDefaultBatchFetchSize()));
        hibernateProperties.putIfAbsent(AvailableSettings.STATEMENT_BATCH_SIZE,
                String.valueOf(hibernate.getJdbcBatchSize()));
        hibernateProperties.putIfAbsent(AvailableSettings.BATCH_VERSIONED_DATA,
                String.valueOf(hibernate.isBatchVersionedData()));
        hibernateProperties.putIfAbsent(AvailableSettings.ORDER_INSERTS,
                String.valueOf(hibernate.isOrderInserts()));
        hibernateProperties.putIfAbsent(AvailableSettings.ORDER_UPDATES,
                String.valueOf(hibernate.isOrderUpdates()));
        hibernateProperties.putIfAbsent(AvailableSettings.IN_CLAUSE_PARAMETER_PADDING,
                String.valueOf(hibernate.isInClauseParameterPadding()));
    }
}
