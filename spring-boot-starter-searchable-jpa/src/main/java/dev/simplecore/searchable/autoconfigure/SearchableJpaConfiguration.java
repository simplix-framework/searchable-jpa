package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.core.utils.SearchableConversionHolder;
import dev.simplecore.searchable.core.utils.SearchablePatternMatchingHolder;
import dev.simplecore.searchable.core.utils.SearchableTimeZoneHolder;
import dev.simplecore.searchable.properties.SearchableProperties;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(EntityManager.class)
@EnableConfigurationProperties(SearchableProperties.class)
public class SearchableJpaConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SearchableJpaConfiguration.class);

    /**
     * Optional bean name a host application may define to publish its canonical
     * application timezone as a single {@link ZoneId} value.
     */
    private static final String APPLICATION_ZONE_ID_BEAN = "applicationZoneId";

    private static final String JACKSON_TIME_ZONE_PROPERTY = "spring.jackson.time-zone";

    /**
     * The conversion service Spring MVC converts request values with. It is the one that holds the
     * converters and formatters an application registers for web input.
     */
    private static final String MVC_CONVERSION_SERVICE_BEAN = "mvcConversionService";

    private final SearchableProperties searchableProperties;
    private final Environment environment;
    private final BeanFactory beanFactory;

    public SearchableJpaConfiguration(SearchableProperties searchableProperties,
                                      Environment environment,
                                      BeanFactory beanFactory) {
        this.searchableProperties = searchableProperties;
        this.environment = environment;
        this.beanFactory = beanFactory;
    }

    @PostConstruct
    public void configureSearchableTimeZone() {
        ZoneId zone = resolveApplicationZoneId();
        SearchableTimeZoneHolder.setZoneId(zone);
        log.info("Searchable value parser timezone set to: {}", zone);
    }

    @PostConstruct
    public void configurePatternMatching() {
        boolean caseInsensitive = searchableProperties.getPatternMatching().isCaseInsensitive();
        SearchablePatternMatchingHolder.setCaseInsensitive(caseInsensitive);
        log.info("Searchable pattern operators ignore case: {}", caseInsensitive);
    }

    @PostConstruct
    public void configureRequestLimits() {
        SearchableProperties.LimitsProperties limits = searchableProperties.getLimits();
        SearchRequestLimits.setStructureLimits(new SearchRequestLimits.Structure(
                limits.getMaxNodes(), limits.getMaxDepth(), limits.getMaxInValues(), limits.getMaxSortFields()));
    }

    /**
     * Connects the library to what the application offers once every bean exists: its messages,
     * which then replace the library's wording key by key, and its conversion service, whose
     * converters make their target types searchable.
     *
     * @param messages the application's message source
     * @param conversionServices the application's conversion services
     * @return the initializer
     */
    @Bean
    public SmartInitializingSingleton searchableApplicationServices(ObjectProvider<MessageSource> messages,
                                                                    ObjectProvider<ConversionService> conversionServices) {
        return () -> {
            messages.ifAvailable(MessageUtils::init);
            ConversionService conversionService = beanFactory.containsBean(MVC_CONVERSION_SERVICE_BEAN)
                    && beanFactory.isTypeMatch(MVC_CONVERSION_SERVICE_BEAN, ConversionService.class)
                    ? beanFactory.getBean(MVC_CONVERSION_SERVICE_BEAN, ConversionService.class)
                    : conversionServices.getIfUnique();
            if (conversionService != null) {
                SearchableConversionHolder.setConversionService(conversionService);
            }
        };
    }

    /**
     * Resolves the zone in which timezone-less search values are read. A zone id that does not
     * exist stops startup: falling through to another zone would shift every date filter without
     * anybody being told.
     */
    private ZoneId resolveApplicationZoneId() {
        String configured = searchableProperties.getDateTime().getDefaultTimezone();
        if (StringUtils.hasText(configured)) {
            return ZoneId.of(configured.trim());
        }
        if (beanFactory.containsBean(APPLICATION_ZONE_ID_BEAN)
                && beanFactory.isTypeMatch(APPLICATION_ZONE_ID_BEAN, ZoneId.class)) {
            return beanFactory.getBean(APPLICATION_ZONE_ID_BEAN, ZoneId.class);
        }
        String jacksonTimeZone = environment.getProperty(JACKSON_TIME_ZONE_PROPERTY);
        if (StringUtils.hasText(jacksonTimeZone)) {
            return ZoneId.of(jacksonTimeZone.trim());
        }
        return ZoneOffset.UTC;
    }

    /**
     * The counter a time-range strip reads its heights from.
     *
     * <p>Offered here so a host application injects it rather than constructing one per service,
     * and conditionally so an application that defines its own keeps it.
     *
     * @param entityManager where the counting query is run
     * @return the counter
     */
    @Bean
    @ConditionalOnMissingBean
    public TimeBucketCounter timeBucketCounter(EntityManager entityManager) {
        return new TimeBucketCounter(entityManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchAggregator searchAggregator(EntityManager entityManager) {
        return new SearchAggregator(entityManager);
    }

    /**
     * Registered only where Spring Boot's Hibernate support is present, since the customizer
     * contract belongs to it.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HibernatePropertiesCustomizer.class)
    static class HibernateSettingsConfiguration {

        @Bean
        HibernatePropertiesCustomizer searchableHibernatePropertiesCustomizer(SearchableProperties properties) {
            return new SearchableHibernatePropertiesCustomizer(properties);
        }
    }
}
