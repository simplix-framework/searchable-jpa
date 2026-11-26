package dev.simplecore.searchable.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "searchable")
public class SearchableProperties {
    @Min(1)
    private int maxPageSize = 1000;
    @Min(1)
    private int defaultPageSize = 20;

    @AssertTrue(message = "defaultPageSize must not exceed maxPageSize")
    public boolean isPageSizeRangeValid() { return defaultPageSize <= maxPageSize; }

    private SwaggerProperties swagger = new SwaggerProperties();

    @Valid
    private HibernateProperties hibernate = new HibernateProperties();

    @Valid
    private DateTimeProperties dateTime = new DateTimeProperties();

    private PatternMatchingProperties patternMatching = new PatternMatchingProperties();

    /** How many identifiers one entity-loading statement carries; a composite key takes one per column. */
    @Min(1)
    private int inClauseBatchSize = 500;

    @Valid
    private WriteProperties write = new WriteProperties();

    @Valid
    private AggregateProperties aggregate = new AggregateProperties();

    @Valid
    private BucketProperties bucket = new BucketProperties();

    @Valid
    private LimitsProperties limits = new LimitsProperties();

    private ParamsProperties params = new ParamsProperties();

    private WebProperties web = new WebProperties();

    @Data
    public static class WriteProperties {
        /** How many entities a search-based update or delete handles between flushes. */
        @Min(1)
        private int chunkSize = 200;

        /**
         * Whether a search-based update or delete is refused when neither a condition nor a server
         * scope narrows it. Off by default: an empty condition then applies to every row.
         */
        private boolean requireFilter = false;
    }

    @Data
    public static class AggregateProperties {
        /** How many distinct values a count per value returns, the most frequent first. */
        @Min(1)
        private int maxGroups = 1000;
    }

    @Data
    public static class BucketProperties {
        /** How many slices one time-bucket count may ask for. */
        @Min(1)
        private int maxBuckets = 512;
    }

    @Data
    public static class LimitsProperties {
        /** The most conditions and groups one search request may hold. */
        @Min(1)
        private int maxNodes = 1000;

        /** The deepest a group of conditions may be nested. */
        @Min(1)
        private int maxDepth = 32;

        /** The most values one condition may list, as IN and NOT_IN do. */
        @Min(1)
        private int maxInValues = 1000;

        /** The most fields one search request may sort by. */
        @Min(1)
        private int maxSortFields = 16;
    }

    @Data
    public static class ParamsProperties {
        /**
         * Whether a query parameter that is not a search parameter of the DTO is left out instead of
         * being refused, when a controller parameter is resolved from the query string. A parameter
         * that names a field of the DTO is always read.
         */
        private boolean ignoreUnknown = false;
    }

    @Data
    public static class WebProperties {
        private ArgumentResolverProperties argumentResolver = new ArgumentResolverProperties();

        private ProblemDetailsProperties problemDetails = new ProblemDetailsProperties();
    }

    @Data
    public static class ArgumentResolverProperties {
        /** Whether a controller parameter {@code @SearchableParams SearchCondition<D>} is resolved from the query string. */
        private boolean enabled = true;
    }

    @Data
    public static class ProblemDetailsProperties {
        /**
         * Whether an invalid search request is answered with status 400 and a problem detail by the
         * library. Off by default, so an application's own exception handling stays in charge.
         */
        private boolean enabled = false;
    }

    @Data
    public static class PatternMatchingProperties {
        /**
         * Whether CONTAINS, STARTS_WITH and ENDS_WITH ignore case by lower-casing the column and the
         * pattern. Off by default: patterns match as the database compares strings.
         */
        private boolean caseInsensitive = false;
    }

    @Data
    public static class SwaggerProperties {
        private boolean enabled = true;
    }

    @Data
    public static class DateTimeProperties {
        /**
         * Application timezone (IANA id, e.g. "Asia/Seoul", "UTC") used to interpret
         * timezone-less search values and expand date-only BETWEEN boundaries.
         * When null, resolution falls back to an application ZoneId bean, then
         * spring.jackson.time-zone, then UTC.
         */
        private String defaultTimezone;
    }

    @Data
    public static class HibernateProperties {
        /** Preserve automatic collection loading; disable to load only explicitly requested collections. */
        private boolean autoFetchCollections = true;

        /**
         * Enable automatic Hibernate optimization configuration.
         * When enabled, the library will automatically configure optimal settings for N+1 prevention.
         */
        private boolean autoOptimization = true;
        
        /**
         * Default batch fetch size for lazy loading.
         * This helps prevent N+1 problems by fetching related entities in batches.
         */
        @Min(1)
        private int defaultBatchFetchSize = 100;

        /**
         * JDBC batch size for bulk operations.
         */
        @Min(1)
        private int jdbcBatchSize = 1000;
        
        /**
         * Has no effect: Hibernate 7 always batches versioned data and no longer reads
         * {@code hibernate.jdbc.batch_versioned_data}. Kept so existing configuration still binds.
         */
        @Deprecated
        private boolean batchVersionedData = true;

        @Deprecated
        @DeprecatedConfigurationProperty(
                reason = "Hibernate 7 always batches versioned data and no longer reads this setting")
        public boolean isBatchVersionedData() {
            return batchVersionedData;
        }
        
        /**
         * Enable order inserts optimization. Off by default: Hibernate orders the inserts of a flush
         * by the entity instances a row references, so an entity that names its parent by an id
         * column, without an insertable association to it, can be written before that parent.
         */
        private boolean orderInserts = false;
        
        /**
         * Enable order updates optimization.
         */
        private boolean orderUpdates = true;
        
        /**
         * Enable IN clause parameter padding for better query plan caching.
         */
        private boolean inClauseParameterPadding = true;
    }
} 