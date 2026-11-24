package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.properties.SearchableProperties;
import dev.simplecore.searchable.web.SearchConditionArgumentResolver;
import dev.simplecore.searchable.web.SearchableExceptionHandler;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * What the starter adds to Spring MVC: the resolver of search parameters and, when asked for, the
 * answer to an invalid search request.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@EnableConfigurationProperties(SearchableProperties.class)
public class SearchableWebConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "searchable.web.argument-resolver", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public WebMvcConfigurer searchConditionArgumentResolverConfigurer(SearchableProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new SearchConditionArgumentResolver(properties.getParams().isIgnoreUnknown()));
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "searchable.web.problem-details", name = "enabled", havingValue = "true")
    public SearchableExceptionHandler searchableExceptionHandler() {
        return new SearchableExceptionHandler();
    }
}
