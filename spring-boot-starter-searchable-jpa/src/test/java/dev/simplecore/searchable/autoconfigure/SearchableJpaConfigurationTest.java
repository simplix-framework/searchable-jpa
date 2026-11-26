package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.core.utils.SearchableTimeZoneHolder;
import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies what the starter registers. The values are asserted on the map Spring Boot hands to
 * Hibernate, through the registered customizer; that they reach a real EntityManagerFactory is
 * asserted by the integration tests in {@code dev.simplecore.searchable.starter}.
 */
class SearchableJpaConfigurationTest {

    private final EntityManager entityManager = mock(EntityManager.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(EntityManager.class, () -> entityManager)
            .withConfiguration(AutoConfigurations.of(SearchableJpaConfiguration.class));

    @AfterEach
    void restoreZone() {
        SearchableTimeZoneHolder.setZoneId(ZoneOffset.UTC);
    }

    /** Applies the registered customizer to the properties an application already set. */
    private static Map<String, Object> customized(ApplicationContext context, Map<String, Object> applicationValues) {
        Map<String, Object> properties = new HashMap<>(applicationValues);
        context.getBean(HibernatePropertiesCustomizer.class).customize(properties);
        return properties;
    }

    // ---- baseline: defaults are applied when auto-optimization is on ----

    @Test
    @DisplayName("auto-optimization adds the Hibernate defaults")
    void autoOptimizationRegistersDefaults() {
        runner.run(context -> {
            Map<String, Object> properties = customized(context, Map.of());
            assertThat(properties)
                    .containsEntry("hibernate.default_batch_fetch_size", "100")
                    .containsEntry("hibernate.jdbc.batch_size", "1000")
                    .containsEntry("hibernate.jdbc.batch_versioned_data", "true")
                    .containsEntry("hibernate.order_inserts", "false")
                    .containsEntry("hibernate.order_updates", "true")
                    .containsEntry("hibernate.query.in_clause_parameter_padding", "true")
                    .doesNotContainKey("hibernate.connection.provider_disables_autocommit");
        });
    }

    // ---- 8-1: the switch actually disables optimization ----

    @Test
    @DisplayName("8-1: setting auto-optimization=false adds no Hibernate default")
    void autoOptimizationCanBeDisabled() {
        runner.withPropertyValues("searchable.hibernate.auto-optimization=false").run(context ->
                assertThat(customized(context, Map.of()).keySet()).noneMatch(key -> key.startsWith("hibernate.")));
    }

    // ---- 8-2: user configuration overrides the library defaults ----

    @Test
    @DisplayName("8-2: a Hibernate property the application set is kept over the library default")
    void userConfigurationOverridesLibraryDefault() {
        runner.run(context -> assertThat(customized(context, Map.of("hibernate.jdbc.batch_size", "500")))
                .containsEntry("hibernate.jdbc.batch_size", "500")
                .containsEntry("hibernate.default_batch_fetch_size", "100"));
    }

    @Test
    @DisplayName("8-2: order-inserts=true turns ordered inserts on")
    void orderInsertsCanBeEnabled() {
        runner.withPropertyValues("searchable.hibernate.order-inserts=true").run(context ->
                assertThat(customized(context, Map.of())).containsEntry("hibernate.order_inserts", "true"));
    }

    // ---- 8-3: invalid batch sizes fail fast at startup ----

    @Test
    @DisplayName("8-3: a non-positive jdbc batch size fails validation at startup")
    void invalidJdbcBatchSizeFailsFast() {
        runner.withPropertyValues("searchable.hibernate.jdbc-batch-size=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("jdbcBatchSize");
        });
    }

    @Test
    @DisplayName("8-3: a non-positive default batch fetch size fails validation at startup")
    void invalidDefaultBatchFetchSizeFailsFast() {
        runner.withPropertyValues("searchable.hibernate.default-batch-fetch-size=-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("defaultBatchFetchSize");
        });
    }

    // ---- time bucket counting ----

    @Test
    @DisplayName("the starter registers a TimeBucketCounter bean")
    void registersTimeBucketCounter() {
        runner.run(context -> assertThat(context).hasSingleBean(TimeBucketCounter.class));
    }

    @Test
    @DisplayName("a TimeBucketCounter defined by the application replaces the auto-registered one")
    void applicationDefinedTimeBucketCounterWins() {
        TimeBucketCounter custom = new TimeBucketCounter(entityManager);

        runner.withBean("customTimeBucketCounter", TimeBucketCounter.class, () -> custom)
                .run(context -> assertThat(context).getBean(TimeBucketCounter.class).isSameAs(custom));
    }

    @Test
    @DisplayName("the search limits are added even when Hibernate optimization is disabled")
    void limitsApplyEvenWhenOptimizationIsDisabled() {
        runner.withPropertyValues("searchable.hibernate.auto-optimization=false",
                "searchable.default-page-size=50", "searchable.max-page-size=2000",
                "searchable.hibernate.auto-fetch-collections=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(customized(context, Map.of()))
                    .containsEntry("searchable.default_page_size", "50")
                    .containsEntry("searchable.max_page_size", "2000")
                    .containsEntry("searchable.auto_fetch_collections", "false");
        });
    }

    @Test
    void defaultPageSizeMustNotExceedMaximum() {
        runner.withPropertyValues("searchable.default-page-size=21", "searchable.max-page-size=20")
                .run(context -> assertThat(context).hasFailed());
    }

    // ---- time zone resolution ----

    @Test
    @DisplayName("the configured zone is used for timezone-less values")
    void configuredZoneIsApplied() {
        runner.withPropertyValues("searchable.date-time.default-timezone=Asia/Seoul").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(SearchableTimeZoneHolder.getZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
        });
    }

    @Test
    @DisplayName("a zone id that does not exist stops startup instead of falling through to another zone")
    void unknownZoneFailsStartup() {
        runner.withPropertyValues("searchable.date-time.default-timezone=Nowhere/Nothing").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Nowhere/Nothing");
        });
        runner.withPropertyValues("spring.jackson.time-zone=Nowhere/Nothing")
                .run(context -> assertThat(context).hasFailed());
    }
}
